package com.mamiyaotaru.voxelmap.chunksync;

import com.mamiyaotaru.voxelmap.VoxelConstants;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public final class ChunkShareService {
    private ChunkShareService() {
    }

    private static com.mamiyaotaru.voxelmap.ExploredChunksManager explored() {
        return VoxelConstants.getVoxelMapInstance().getExploredChunksManager();
    }

    private static com.mamiyaotaru.voxelmap.NewerNewChunksManager newold() {
        return VoxelConstants.getVoxelMapInstance().getNewerNewChunksManager();
    }

    private static boolean isZip(Path path) {
        return path.getFileName().toString().toLowerCase().endsWith(".zip");
    }

    public static String slugFor(String name) {
        String trimmed = name == null ? "" : name.trim().replaceAll("[^A-Za-z0-9_.-]", "_");
        return trimmed.isEmpty() ? "player" : trimmed;
    }

    public static int exportBundle(Path target, String playerName) throws IOException {
        Map<String, ChunkShareCodec.Payload> byDimension = gatherPayloads(playerName);
        if (isZip(target)) {
            Path parent = target.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(target))) {
                for (Map.Entry<String, ChunkShareCodec.Payload> e : byDimension.entrySet()) {
                    zip.putNextEntry(new ZipEntry(e.getKey() + ".txt"));
                    zip.write(ChunkShareCodec.write(e.getValue()).getBytes(StandardCharsets.UTF_8));
                    zip.closeEntry();
                }
            }
        } else {
            Files.createDirectories(target);
            for (Map.Entry<String, ChunkShareCodec.Payload> e : byDimension.entrySet()) {
                Files.writeString(target.resolve(e.getKey() + ".txt"), ChunkShareCodec.write(e.getValue()), StandardCharsets.UTF_8);
            }
        }
        return countChunks(byDimension);
    }

    public static byte[] exportBundleBytes(String playerName) throws IOException {
        Map<String, ChunkShareCodec.Payload> byDimension = gatherPayloads(playerName);
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bos)) {
            for (Map.Entry<String, ChunkShareCodec.Payload> e : byDimension.entrySet()) {
                zip.putNextEntry(new ZipEntry(e.getKey() + ".txt"));
                zip.write(ChunkShareCodec.write(e.getValue()).getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return bos.toByteArray();
    }

    public static int importBundle(Path path) throws IOException {
        if (Files.isRegularFile(path) && !isZip(path)) {
            ChunkShareCodec.Payload payload = ChunkShareCodec.parse(Files.readAllLines(path, StandardCharsets.UTF_8));
            return explored().importExploredChunks(payload.explored()) + newold().importNewOld(payload.newold());
        }
        int[] total = {0};
        forEachDimension(path, (dimension, payload) -> {
            total[0] += explored().importDimensionExplored(dimension, payload.explored());
            total[0] += newold().importDimensionNewOld(dimension, payload.newold());
        });
        return total[0];
    }

    public static int importBundleAsPlayer(Path path, String playerOverride) throws IOException {
        int[] total = {0};
        forEachDimension(path, (dimension, payload) -> {
            String name = playerOverride != null && !playerOverride.isBlank()
                    ? playerOverride
                    : (payload.player() != null && !payload.player().isBlank() ? payload.player() : "player");
            String slug = slugFor(name);
            total[0] += explored().importPlayerExplored(slug, dimension, payload.explored());
            total[0] += newold().importPlayerNewOld(slug, dimension, payload.newold());
        });
        return total[0];
    }

    private interface DimensionConsumer {
        void accept(String dimension, ChunkShareCodec.Payload payload);
    }

    private static void forEachDimension(Path path, DimensionConsumer consumer) throws IOException {
        if (isZip(path) && Files.isRegularFile(path)) {
            try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(path))) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    String name = baseName(entry.getName());
                    if (!name.endsWith(".txt")) {
                        continue;
                    }
                    ChunkShareCodec.Payload payload = parseContent(new String(zip.readAllBytes(), StandardCharsets.UTF_8));
                    consumer.accept(stripTxt(name), payload);
                }
            }
            return;
        }
        if (Files.isDirectory(path)) {
            List<Path> files = new ArrayList<>();
            try (var stream = Files.list(path)) {
                stream.filter(p -> p.getFileName().toString().endsWith(".txt")).forEach(files::add);
            }
            for (Path file : files) {
                ChunkShareCodec.Payload payload = parseContent(Files.readString(file, StandardCharsets.UTF_8));
                consumer.accept(stripTxt(file.getFileName().toString()), payload);
            }
            return;
        }
        if (Files.isRegularFile(path)) {
            ChunkShareCodec.Payload payload = parseContent(Files.readString(path, StandardCharsets.UTF_8));
            consumer.accept(stripTxt(path.getFileName().toString()), payload);
            return;
        }
        throw new FileNotFoundException(path.toString());
    }

    private static ChunkShareCodec.Payload parseContent(String content) {
        return ChunkShareCodec.parse(List.of(content.split("\n", -1)));
    }

    private static Map<String, ChunkShareCodec.Payload> gatherPayloads(String playerName) {
        Map<String, long[]> exploredByDim = explored().exportAllDimensionsExplored();
        Map<String, Map<String, long[]>> newoldByDim = newold().exportAllDimensionsNewOld();
        Set<String> dimensions = new LinkedHashSet<>();
        dimensions.addAll(exploredByDim.keySet());
        dimensions.addAll(newoldByDim.keySet());
        Map<String, ChunkShareCodec.Payload> out = new LinkedHashMap<>();
        for (String dimension : dimensions) {
            out.put(dimension, new ChunkShareCodec.Payload(playerName,
                    exploredByDim.getOrDefault(dimension, new long[0]),
                    newoldByDim.getOrDefault(dimension, Map.of())));
        }
        return out;
    }

    private static int countChunks(Map<String, ChunkShareCodec.Payload> byDimension) {
        int total = 0;
        for (ChunkShareCodec.Payload payload : byDimension.values()) {
            total += payload.explored().length;
            for (long[] coords : payload.newold().values()) {
                total += coords.length;
            }
        }
        return total;
    }

    private static String baseName(String entryName) {
        int slash = Math.max(entryName.lastIndexOf('/'), entryName.lastIndexOf('\\'));
        return slash >= 0 ? entryName.substring(slash + 1) : entryName;
    }

    private static String stripTxt(String fileName) {
        return fileName.endsWith(".txt") ? fileName.substring(0, fileName.length() - ".txt".length()) : fileName;
    }
}
