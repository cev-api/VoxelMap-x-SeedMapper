package com.mamiyaotaru.voxelmap.chunksync;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;

/**
 * uploads/downloads an (already encrypted) chunk-share blob to a free anonymous file host, the host is
 * pluggable so a rate-limited or down service can be swapped with one command
 * <ul>
 *   <li>{@link Host#LITTERBOX} -- temporary (72h), multi-download; the default, best for group sharing.</li>
 *   <li>{@link Host#FILE_IO} -- one-time download (deleted after the first fetch); for quick 1-to-1 hand-offs.</li>
 * </ul>
 */
public final class ChunkShareTransport {
    private static final String USER_AGENT = "Mozilla/5.0 (VoxelMap-x-SeedMapper ChunkShare)";

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private ChunkShareTransport() {
    }

    public enum Host {
        LITTERBOX("litterbox"),
        FILE_IO("file.io");

        public final String id;

        Host(String id) {
            this.id = id;
        }

        public static Host fromId(String id) {
            if (id != null) {
                String norm = id.trim().toLowerCase(Locale.ROOT);
                for (Host h : values()) {
                    if (h.id.equals(norm)) {
                        return h;
                    }
                }
            }
            return LITTERBOX;
        }
    }

    public static String upload(Host host, byte[] data) throws IOException, InterruptedException {
        return switch (host) {
            case LITTERBOX -> uploadLitterbox(data);
            case FILE_IO -> uploadFileIo(data);
        };
    }

    public static byte[] download(Host host, String code) throws IOException, InterruptedException {
        String url = resolveUrl(host, code);
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", USER_AGENT)
                .timeout(Duration.ofSeconds(60))
                .GET()
                .build();
        HttpResponse<byte[]> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() / 100 != 2) {
            throw new IOException("Download failed (HTTP " + response.statusCode() + "). The link may have expired or been used already.");
        }
        return response.body();
    }

    public static String resolveUrl(Host host, String code) {
        String trimmed = code == null ? "" : code.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed;
        }
        return switch (host) {
            case LITTERBOX -> "https://litter.catbox.moe/" + trimmed;
            case FILE_IO -> "https://file.io/" + trimmed;
        };
    }

    private static String uploadLitterbox(byte[] data) throws IOException, InterruptedException {
        MultipartBody body = new MultipartBody();
        body.field("reqtype", "fileupload");
        body.field("time", "72h");
        body.file("fileToUpload", "chunks.vmcs", data);
        String response = post("https://litterbox.catbox.moe/resources/internals/api.php", body);
        String url = response.trim();
        if (!url.startsWith("http")) {
            throw new IOException("litterbox upload failed: " + truncate(url));
        }
        return url;
    }

    private static String uploadFileIo(byte[] data) throws IOException, InterruptedException {
        MultipartBody body = new MultipartBody();
        body.field("expires", "1d");
        body.field("maxDownloads", "1");
        body.field("autoDelete", "true");
        body.file("file", "chunks.vmcs", data);
        String response = post("https://file.io", body);
        String link = extractJsonString(response, "link");
        if (link == null || !link.startsWith("http")) {
            throw new IOException("file.io upload failed: " + truncate(response));
        }
        return link;
    }

    private static String post(String url, MultipartBody body) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", USER_AGENT)
                .header("Content-Type", "multipart/form-data; boundary=" + body.boundary())
                .header("Accept", "*/*")
                .timeout(Duration.ofSeconds(120))
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.finish()))
                .build();
        HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() / 100 != 2) {
            throw new IOException("upload rejected (HTTP " + response.statusCode() + "): " + truncate(response.body()));
        }
        return response.body();
    }

    private static String extractJsonString(String json, String key) {
        String needle = "\"" + key + "\"";
        int k = json.indexOf(needle);
        if (k < 0) {
            return null;
        }
        int colon = json.indexOf(':', k + needle.length());
        if (colon < 0) {
            return null;
        }
        int firstQuote = json.indexOf('"', colon + 1);
        if (firstQuote < 0) {
            return null;
        }
        StringBuilder out = new StringBuilder();
        for (int i = firstQuote + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '\\' && i + 1 < json.length()) {
                char next = json.charAt(++i);
                out.append(next == '/' ? '/' : next);
            } else if (c == '"') {
                return out.toString();
            } else {
                out.append(c);
            }
        }
        return null;
    }

    private static String truncate(String s) {
        if (s == null) {
            return "(no response)";
        }
        s = s.replaceAll("\\s+", " ").trim();
        return s.length() > 200 ? s.substring(0, 200) + "..." : s;
    }

    private static final class MultipartBody {
        private final String boundary = "----VMChunkShare" + Long.toHexString(System.nanoTime());
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        String boundary() {
            return boundary;
        }

        void field(String name, String value) throws IOException {
            write("--" + boundary + "\r\n");
            write("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n");
            write(value + "\r\n");
        }

        void file(String name, String filename, byte[] data) throws IOException {
            write("--" + boundary + "\r\n");
            write("Content-Disposition: form-data; name=\"" + name + "\"; filename=\"" + filename + "\"\r\n");
            write("Content-Type: application/octet-stream\r\n\r\n");
            buffer.write(data);
            write("\r\n");
        }

        byte[] finish() throws IOException {
            write("--" + boundary + "--\r\n");
            return buffer.toByteArray();
        }

        private void write(String s) throws IOException {
            buffer.write(s.getBytes(StandardCharsets.UTF_8));
        }
    }
}
