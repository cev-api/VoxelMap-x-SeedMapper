package com.mamiyaotaru.voxelmap.persistent;

import com.mamiyaotaru.voxelmap.VoxelConstants;
import com.mamiyaotaru.voxelmap.util.CompressionUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Append-only, independently readable storage for map regions.
 *
 * <p>Each region update is one record. The in-memory index points to the newest
 * record, so reads seek directly to the compressed payload and never unpack the
 * rest of the file. Incomplete records at the end are ignored after a crash.</p>
 */
final class MapRegionPack {
    private static final int MAGIC = 0x564D5031; // VMP1
    private static final int RECORD_MAGIC = 0x52454331; // REC1
    private static final int FORMAT_VERSION = 1;
    private static final int PAYLOAD_VERSION = 1;
    private static final int HEADER_BYTES = 16;
    private static final int RECORD_HEADER_BYTES = 20;
    private static final int MAX_PAYLOAD_BYTES = 128 * 1024 * 1024;
    private static final long COMPACTION_MIN_BYTES = 256L * 1024L * 1024L;
    private static final ConcurrentMap<Path, MapRegionPack> OPEN_PACKS = new ConcurrentHashMap<>();

    private final Path directory;
    private final Path packPath;
    private final ReentrantLock lock = new ReentrantLock();
    private final Map<Long, List<Entry>> history = new HashMap<>();
    private final AtomicBoolean migrationScheduled = new AtomicBoolean();
    private long recordBytes;
    private long liveRecordBytes;

    private MapRegionPack(Path directory) {
        this.directory = directory;
        this.packPath = directory.resolve("regions.vmp");
        loadIndex();
        scheduleLegacyMigration();
    }

    static MapRegionPack forDirectory(File directory) {
        Path normalized = directory.toPath().toAbsolutePath().normalize();
        return OPEN_PACKS.computeIfAbsent(normalized, MapRegionPack::new);
    }

    PackedRegion read(int regionX, int regionZ) {
        lock.lock();
        try {
            return readLatestLocked(regionX, regionZ);
        } finally {
            lock.unlock();
        }
    }

    /** Reads a legacy ZIP, writes a verified replacement, and only then removes the ZIP. */
    PackedRegion readOrMigrate(File legacyFile, int regionX, int regionZ) {
        lock.lock();
        try {
            PackedRegion packed = readLatestLocked(regionX, regionZ);
            if (packed != null) {
                deleteLegacy(legacyFile);
                return packed;
            }
            if (!legacyFile.isFile()) return null;
            PackedRegion legacy = readLegacyZip(legacyFile);
            if (legacy == null) return null;
            appendLocked(regionX, regionZ, legacy);
            deleteLegacy(legacyFile);
            return legacy;
        } catch (Exception exception) {
            VoxelConstants.getLogger().warn("Could not migrate map region {}", legacyFile, exception);
            return null;
        } finally {
            lock.unlock();
        }
    }

    void write(int regionX, int regionZ, byte[] data, byte[] key, byte[] biomes, byte[] control) throws IOException {
        lock.lock();
        try {
            appendLocked(regionX, regionZ, new PackedRegion(data, key, biomes, control));
        } finally {
            lock.unlock();
        }
    }

    private void loadIndex() {
        boolean invalid = false;
        lock.lock();
        try {
            if (!Files.isRegularFile(packPath)) return;
            try (RandomAccessFile file = new RandomAccessFile(packPath.toFile(), "rw")) {
                if (file.length() < HEADER_BYTES || file.readInt() != MAGIC || file.readInt() != FORMAT_VERSION) {
                    invalid = true;
                } else {
                    file.readLong();
                    long offset = HEADER_BYTES;
                    long length = file.length();
                    while (offset + RECORD_HEADER_BYTES <= length) {
                        file.seek(offset);
                        if (file.readInt() != RECORD_MAGIC) break;
                        int regionX = file.readInt();
                        int regionZ = file.readInt();
                        int payloadLength = file.readInt();
                        int checksum = file.readInt();
                        if (payloadLength < 0 || payloadLength > MAX_PAYLOAD_BYTES
                                || offset + RECORD_HEADER_BYTES + payloadLength > length) break;
                        Entry entry = new Entry(offset, offset + RECORD_HEADER_BYTES, payloadLength, checksum);
                        history.computeIfAbsent(key(regionX, regionZ), ignored -> new ArrayList<>()).add(entry);
                        recordBytes += RECORD_HEADER_BYTES + payloadLength;
                        offset += RECORD_HEADER_BYTES + payloadLength;
                    }
                    for (List<Entry> entries : history.values()) {
                        if (!entries.isEmpty()) liveRecordBytes += entries.get(entries.size() - 1).recordBytes();
                    }
                    // A crash can leave a partial record at EOF. Remove only that
                    // unindexed tail so future appends remain discoverable.
                    if (offset < length) file.setLength(offset);
                }
            }
        } catch (IOException exception) {
            VoxelConstants.getLogger().warn("Could not index map region pack {}", packPath, exception);
        } finally {
            lock.unlock();
        }
        if (invalid) quarantineCorruptPack();
    }

    private void appendLocked(int regionX, int regionZ, PackedRegion region) throws IOException {
        Files.createDirectories(directory);
        byte[] payload = encode(region);
        if (payload.length > MAX_PAYLOAD_BYTES) {
            throw new IOException("Map region payload is too large: " + payload.length);
        }
        CRC32 crc = new CRC32();
        crc.update(payload);
        int checksum = (int) crc.getValue();
        long recordOffset;
        try (RandomAccessFile file = new RandomAccessFile(packPath.toFile(), "rw")) {
            if (file.length() == 0L) writeHeader(file);
            else if (file.length() < HEADER_BYTES) throw new IOException("Map region pack header is incomplete");
            recordOffset = file.length();
            file.seek(recordOffset);
            file.writeInt(RECORD_MAGIC);
            file.writeInt(regionX);
            file.writeInt(regionZ);
            file.writeInt(payload.length);
            file.writeInt(checksum);
            file.write(payload);
            file.getFD().sync();
        }

        Entry entry = new Entry(recordOffset, recordOffset + RECORD_HEADER_BYTES, payload.length, checksum);
        List<Entry> entries = history.computeIfAbsent(key(regionX, regionZ), ignored -> new ArrayList<>());
        if (!entries.isEmpty()) liveRecordBytes -= entries.get(entries.size() - 1).recordBytes();
        entries.add(entry);
        liveRecordBytes += entry.recordBytes();
        recordBytes += entry.recordBytes();
        if (recordBytes > COMPACTION_MIN_BYTES && recordBytes > liveRecordBytes * 2L) compactLocked();
    }

    private PackedRegion readLatestLocked(int regionX, int regionZ) {
        List<Entry> entries = history.get(key(regionX, regionZ));
        if (entries == null) return null;
        for (int index = entries.size() - 1; index >= 0; index--) {
            Entry entry = entries.get(index);
            try (RandomAccessFile file = new RandomAccessFile(packPath.toFile(), "r")) {
                file.seek(entry.payloadOffset());
                byte[] payload = new byte[entry.payloadLength()];
                file.readFully(payload);
                CRC32 crc = new CRC32();
                crc.update(payload);
                if ((int) crc.getValue() != entry.checksum()) continue;
                return decode(payload);
            } catch (IOException exception) {
                // Try the previous record for this region. The append log keeps it as a fallback.
            }
        }
        return null;
    }

    private PackedRegion readLegacyZip(File legacyFile) throws IOException {
        try (ZipFile zip = new ZipFile(legacyFile)) {
            byte[] data = readEntry(zip, "data");
            if (data == null) return null;
            return new PackedRegion(data, readEntry(zip, "key"), readEntry(zip, "biomes"), readEntry(zip, "control"));
        }
    }

    private static byte[] readEntry(ZipFile zip, String name) throws IOException {
        ZipEntry entry = zip.getEntry(name);
        if (entry == null) return null;
        try (var input = zip.getInputStream(entry)) {
            return input.readAllBytes();
        }
    }

    private void scheduleLegacyMigration() {
        if (!migrationScheduled.compareAndSet(false, true)) return;
        try {
            ThreadManager.executorService.execute(this::migrateLegacyFiles);
        } catch (RuntimeException exception) {
            migrationScheduled.set(false);
        }
    }

    private void migrateLegacyFiles() {
        int total = countLegacyFiles();
        try (WorldMapLoadStatus.Task progress = WorldMapLoadStatus.begin("converting legacy map regions", total);
             DirectoryStream<Path> files = Files.newDirectoryStream(directory, "*.zip")) {
            for (Path path : files) {
                if (Thread.currentThread().isInterrupted()) break;
                int[] coordinates = parseCoordinates(path.getFileName().toString());
                if (coordinates != null) {
                    readOrMigrate(path.toFile(), coordinates[0], coordinates[1]);
                }
                progress.step();
            }
        } catch (IOException exception) {
            VoxelConstants.getLogger().debug("Legacy map migration stopped for {}", directory, exception);
        }
    }

    private int countLegacyFiles() {
        int count = 0;
        try (DirectoryStream<Path> files = Files.newDirectoryStream(directory, "*.zip")) {
            for (Path ignored : files) count++;
        } catch (IOException ignored) {
        }
        return count;
    }

    private static int[] parseCoordinates(String name) {
        if (!name.endsWith(".zip")) return null;
        String[] parts = name.substring(0, name.length() - 4).split(",", 2);
        if (parts.length != 2) return null;
        try {
            return new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1])};
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private void deleteLegacy(File legacyFile) {
        try {
            Files.deleteIfExists(legacyFile.toPath());
        } catch (IOException exception) {
            VoxelConstants.getLogger().debug("Could not remove migrated map region {}", legacyFile, exception);
        }
    }

    private void compactLocked() {
        Path temporary = packPath.resolveSibling(packPath.getFileName() + ".tmp");
        Map<Long, List<Entry>> compacted = new HashMap<>();
        long compactedBytes = 0L;
        try (RandomAccessFile source = new RandomAccessFile(packPath.toFile(), "r");
             RandomAccessFile target = new RandomAccessFile(temporary.toFile(), "rw")) {
            target.setLength(0L);
            writeHeader(target);
            for (Map.Entry<Long, List<Entry>> item : history.entrySet()) {
                List<Entry> entries = item.getValue();
                if (entries.isEmpty()) continue;
                Entry current = entries.get(entries.size() - 1);
                source.seek(current.recordOffset());
                byte[] record = new byte[Math.toIntExact(current.recordBytes())];
                source.readFully(record);
                long newOffset = target.length();
                target.seek(newOffset);
                target.write(record);
                Entry replacement = new Entry(newOffset, newOffset + RECORD_HEADER_BYTES,
                        current.payloadLength(), current.checksum());
                compacted.put(item.getKey(), new ArrayList<>(List.of(replacement)));
                compactedBytes += replacement.recordBytes();
            }
            target.getFD().sync();
        } catch (IOException exception) {
            try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
            return;
        }

        try {
            Files.move(temporary, packPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException atomicFailure) {
            try {
                Files.move(temporary, packPath, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException failure) {
                VoxelConstants.getLogger().warn("Could not compact map region pack {}", packPath, failure);
                return;
            }
        }
        history.clear();
        history.putAll(compacted);
        recordBytes = compactedBytes;
        liveRecordBytes = compactedBytes;
    }

    private static void writeHeader(RandomAccessFile file) throws IOException {
        file.seek(0L);
        file.writeInt(MAGIC);
        file.writeInt(FORMAT_VERSION);
        file.writeLong(System.currentTimeMillis());
    }

    private static byte[] encode(PackedRegion region) throws IOException {
        byte[] compressedData = CompressionUtils.compress(region.data());
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream(compressedData.length + 256);
             DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeInt(PAYLOAD_VERSION);
            writeBytes(output, compressedData);
            writeBytes(output, region.key());
            writeBytes(output, region.biomes());
            writeBytes(output, region.control());
            output.flush();
            return bytes.toByteArray();
        }
    }

    private static PackedRegion decode(byte[] payload) throws IOException {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(payload))) {
            if (input.readInt() != PAYLOAD_VERSION) throw new IOException("Unsupported map payload version");
            byte[] compressedData = readBytes(input);
            byte[] data;
            try {
                data = CompressionUtils.decompress(compressedData);
            } catch (Exception exception) {
                throw new IOException("Corrupt compressed map data", exception);
            }
            return new PackedRegion(data, readBytes(input), readBytes(input), readBytes(input));
        }
    }

    private static void writeBytes(DataOutputStream output, byte[] value) throws IOException {
        if (value == null) {
            output.writeInt(-1);
        } else {
            output.writeInt(value.length);
            output.write(value);
        }
    }

    private static byte[] readBytes(DataInputStream input) throws IOException {
        int length = input.readInt();
        if (length < -1 || length > MAX_PAYLOAD_BYTES) throw new IOException("Invalid map payload length");
        if (length == -1) return null;
        byte[] value = new byte[length];
        input.readFully(value);
        return value;
    }

    private void quarantineCorruptPack() {
        try {
            Path backup = packPath.resolveSibling(packPath.getFileName() + ".corrupt-" + System.currentTimeMillis());
            Files.move(packPath, backup, StandardCopyOption.REPLACE_EXISTING);
            VoxelConstants.getLogger().warn("Map region pack was invalid; preserved it as {}", backup);
        } catch (IOException exception) {
            VoxelConstants.getLogger().warn("Map region pack is invalid: {}", packPath, exception);
        }
    }

    private static long key(int regionX, int regionZ) {
        return ((long) regionX << 32) ^ (regionZ & 0xFFFFFFFFL);
    }

    record PackedRegion(byte[] data, byte[] key, byte[] biomes, byte[] control) { }

    private record Entry(long recordOffset, long payloadOffset, int payloadLength, int checksum) {
        long recordBytes() { return RECORD_HEADER_BYTES + payloadLength; }
    }
}
