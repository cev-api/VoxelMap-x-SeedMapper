package com.mamiyaotaru.voxelmap.persistent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/** Edits only the seeds header, preserving waypoint records, comments and line endings. */
public final class ServerSeedStore {
    private static final Pattern HEADER = Pattern.compile("(?m)^seeds:([^\r\n]*)");
    private ServerSeedStore() { }
    public static Map<String, String> read(Path file) throws IOException {
        return parse(Files.readString(file, StandardCharsets.UTF_8));
    }
    private static Map<String, String> parse(String text) {
        Map<String, String> seeds = new LinkedHashMap<>();
        var match = HEADER.matcher(text);
        if (match.find()) for (String pair : match.group(1).split(",")) {
            int separator = pair.indexOf('#');
            if (separator > 0) seeds.put(pair.substring(0, separator), pair.substring(separator + 1));
        }
        return seeds;
    }
    public static synchronized void update(Path file, String key, String seed) throws IOException {
        if (key.isBlank() || key.indexOf('\n') >= 0 || key.indexOf('\r') >= 0 || key.indexOf('#') >= 0 || key.indexOf(',') >= 0 || seed.indexOf('#') >= 0
                || seed.indexOf(',') >= 0 || seed.indexOf('\n') >= 0 || seed.indexOf('\r') >= 0)
            throw new IllegalArgumentException("Seed cannot contain commas, # or newlines");
        String text = Files.exists(file) ? Files.readString(file, StandardCharsets.UTF_8) : "";
        Map<String, String> seeds = parse(text);
        if (seed.isBlank()) seeds.remove(key); else seeds.put(key, seed);
        StringBuilder header = new StringBuilder("seeds:");
        seeds.forEach((world, value) -> header.append(world).append('#').append(value).append(','));
        var match = HEADER.matcher(text);
        String updated = match.find() ? text.substring(0, match.start()) + header + text.substring(match.end())
                : header + (text.contains("\r\n") ? "\r\n" : "\n") + text;
        Files.createDirectories(file.toAbsolutePath().getParent());
        Path temporary = Files.createTempFile(file.toAbsolutePath().getParent(), "voxelmap-seeds-", ".tmp");
        try {
            Files.writeString(temporary, updated, StandardCharsets.UTF_8);
            try { Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (java.nio.file.AtomicMoveNotSupportedException unsupported) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }
}
