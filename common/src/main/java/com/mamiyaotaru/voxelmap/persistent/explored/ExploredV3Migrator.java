package com.mamiyaotaru.voxelmap.persistent.explored;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.stream.Stream;


public final class ExploredV3Migrator {
    private static final int V2_MAGIC = 0x45585032;

    private ExploredV3Migrator() {
    }

    public static boolean isComplete(Path v3Dir) {
        Path manifest = v3Dir.resolve("manifest.json");
        if (Files.notExists(manifest)) {
            return false;
        }
        try {
            String json = Files.readString(manifest, StandardCharsets.UTF_8).replaceAll("\\s+", "");
            return json.contains("\"formatVersion\":3") && json.contains("\"migrationComplete\":true");
        } catch (IOException ignored) {
            return false;
        }
    }

    public static void migrate(Path v1TextFile, Path v2RegionDir, Path v3Dir) {
        ExploredDiskStore store = new ExploredDiskStore(v3Dir);
        int chunks = 0;
        chunks += importV2(v2RegionDir, store);
        chunks += importV1(v1TextFile, store);
        store.flush();
        writeManifest(v3Dir, chunks);
    }

    private static int importV2(Path v2RegionDir, ExploredDiskStore store) {
        if (v2RegionDir == null || Files.notExists(v2RegionDir)) {
            return 0;
        }
        int imported = 0;
        try (Stream<Path> files = Files.list(v2RegionDir)) {
            for (Path file : (Iterable<Path>) files.filter(p -> p.getFileName().toString().endsWith(".bin"))::iterator) {
                imported += importV2Region(file, store);
            }
        } catch (IOException ignored) {
        }
        return imported;
    }

    private static int importV2Region(Path file, ExploredDiskStore store) {
        try (InputStream in = Files.newInputStream(file)) {
            int magic = readInt(in);
            readInt(in); // version
            readInt(in); // regionShift
            int rx = readInt(in);
            int rz = readInt(in);
            if (magic != V2_MAGIC) {
                return 0;
            }
            byte[] body = in.readNBytes(ExploredTile.BYTE_SIZE);
            if (body.length < ExploredTile.BYTE_SIZE) {
                return 0;
            }
            ExploredTile tile = ExploredTile.fromBytes(body);
            int baseX = rx << 5;
            int baseZ = rz << 5;
            int count = 0;
            for (int lx = 0; lx < ExploredTile.SIDE; lx++) {
                for (int lz = 0; lz < ExploredTile.SIDE; lz++) {
                    if (tile.get(lx, lz)) {
                        store.setChunk(baseX + lx, baseZ + lz);
                        count++;
                    }
                }
            }
            return count;
        } catch (IOException ignored) {
            return 0;
        }
    }

    private static int importV1(Path v1TextFile, ExploredDiskStore store) {
        if (v1TextFile == null) {
            return 0;
        }
        int imported = importV1File(v1TextFile, store);
        Path parent = v1TextFile.getParent();
        if (parent != null) {
            String fileName = v1TextFile.getFileName().toString();
            String worldKey = fileName.endsWith(".txt") ? fileName.substring(0, fileName.length() - 4) : fileName;
            Path backupDir = parent.resolve("legacy_backup");
            if (Files.isDirectory(backupDir)) {
                try (Stream<Path> files = Files.list(backupDir)) {
                    for (Path file : (Iterable<Path>) files.filter(p -> {
                        String n = p.getFileName().toString();
                        return n.startsWith(worldKey + ".") && n.endsWith(".txt");
                    })::iterator) {
                        imported += importV1File(file, store);
                    }
                } catch (IOException ignored) {
                }
            }
        }
        return imported;
    }

    private static int importV1File(Path file, ExploredDiskStore store) {
        if (file == null || Files.notExists(file)) {
            return 0;
        }
        int imported = 0;
        try (Stream<String> lines = Files.lines(file, StandardCharsets.UTF_8)) {
            for (String line : (Iterable<String>) lines::iterator) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                String[] parts = trimmed.split(",");
                if (parts.length != 2) {
                    continue;
                }
                try {
                    store.setChunk(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()));
                    imported++;
                } catch (NumberFormatException ignored) {
                }
            }
        } catch (IOException ignored) {
        }
        return imported;
    }

    private static void writeManifest(Path v3Dir, int migratedChunks) {
        String json = "{\n"
                + "  \"formatVersion\": 3,\n"
                + "  \"migrationComplete\": true,\n"
                + "  \"migratedChunks\": " + migratedChunks + "\n"
                + "}\n";
        try {
            Files.createDirectories(v3Dir);
            Path manifest = v3Dir.resolve("manifest.json");
            Path temp = manifest.resolveSibling("manifest.json.tmp");
            Files.writeString(temp, json, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            Files.move(temp, manifest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {
        }
    }

    private static int readInt(InputStream in) throws IOException {
        int b1 = in.read();
        int b2 = in.read();
        int b3 = in.read();
        int b4 = in.read();
        if ((b1 | b2 | b3 | b4) < 0) {
            throw new IOException("Unexpected end of V2 region file");
        }
        return (b1 << 24) | (b2 << 16) | (b3 << 8) | b4;
    }
}
