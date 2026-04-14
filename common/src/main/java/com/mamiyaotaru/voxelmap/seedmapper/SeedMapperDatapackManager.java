package com.mamiyaotaru.voxelmap.seedmapper;

import net.minecraft.client.Minecraft;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class SeedMapperDatapackManager {
    private SeedMapperDatapackManager() {
    }

    public static ImportResult importFromUrl(String urlString) throws IOException {
        if (urlString == null || urlString.isBlank()) {
            throw new IOException("Datapack URL is empty");
        }

        URL url = new URL(urlString.trim());
        URLConnection connection = url.openConnection();
        connection.setRequestProperty("User-Agent", "VoxelMap-SeedMapper");
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(20000);

        Path tempZip = Files.createTempFile("voxelmap-seedmapper-datapack", ".zip");
        try (InputStream input = connection.getInputStream()) {
            Files.copy(input, tempZip, StandardCopyOption.REPLACE_EXISTING);
        }

        Path cacheRoot = Minecraft.getInstance().gameDirectory.toPath().resolve("voxelmap").resolve("seedmapper").resolve("datapacks");
        Files.createDirectories(cacheRoot);
        String hash = sha1Hex(tempZip);
        Path unpackRoot = cacheRoot.resolve(hash);
        if (!Files.exists(unpackRoot)) {
            Files.createDirectories(unpackRoot);
            unzip(tempZip, unpackRoot);
        }
        Files.deleteIfExists(tempZip);

        Path datapackRoot = resolveDatapackRoot(unpackRoot);
        List<String> structures = listStructureIds(datapackRoot);
        return new ImportResult(datapackRoot, structures);
    }

    public static List<String> readImportedStructureIds(String datapackRootPath) {
        if (datapackRootPath == null || datapackRootPath.isBlank()) {
            return List.of();
        }
        Path root = Path.of(datapackRootPath);
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try {
            return listStructureIds(root);
        } catch (IOException ignored) {
            return List.of();
        }
    }

    private static List<String> listStructureIds(Path datapackRoot) throws IOException {
        Path data = datapackRoot.resolve("data");
        if (!Files.exists(data)) {
            return List.of();
        }
        ArrayList<String> ids = new ArrayList<>();
        try (DirectoryStream<Path> namespaces = Files.newDirectoryStream(data)) {
            for (Path namespace : namespaces) {
                if (!Files.isDirectory(namespace)) {
                    continue;
                }
                String ns = namespace.getFileName().toString();
                Path structures = namespace.resolve("worldgen").resolve("structure");
                if (!Files.isDirectory(structures)) {
                    continue;
                }
                try (DirectoryStream<Path> files = Files.newDirectoryStream(structures, "*.json")) {
                    for (Path file : files) {
                        String id = file.getFileName().toString();
                        if (id.endsWith(".json")) {
                            id = id.substring(0, id.length() - 5);
                        }
                        ids.add((ns + ":" + id).toLowerCase(Locale.ROOT));
                    }
                }
            }
        }
        ids.sort(String::compareTo);
        return ids;
    }

    private static Path resolveDatapackRoot(Path baseDir) throws IOException {
        if (Files.exists(baseDir.resolve("data"))) {
            return baseDir;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(baseDir)) {
            Path onlyDir = null;
            for (Path entry : stream) {
                if (!Files.isDirectory(entry)) {
                    continue;
                }
                if (onlyDir != null) {
                    return baseDir;
                }
                onlyDir = entry;
            }
            if (onlyDir != null && Files.exists(onlyDir.resolve("data"))) {
                return onlyDir;
            }
        }
        return baseDir;
    }

    private static void unzip(Path zipFile, Path targetDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(Files.newInputStream(zipFile)))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String normalized = entry.getName().replace('\\', '/');
                Path target = targetDir.resolve(normalized).normalize();
                if (!target.startsWith(targetDir)) {
                    throw new IOException("Zip entry outside target directory");
                }
                Files.createDirectories(target.getParent());
                Files.copy(zis, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static String sha1Hex(Path file) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-1 not available", e);
        }
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : digest.digest()) {
            sb.append(String.format(Locale.ROOT, "%02x", b));
        }
        return sb.toString();
    }

    public record ImportResult(Path datapackRoot, List<String> structureIds) {
    }
}
