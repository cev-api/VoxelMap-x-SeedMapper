package com.mamiyaotaru.voxelmap.chunksync;

import com.mamiyaotaru.voxelmap.VoxelConstants;
import com.mamiyaotaru.voxelmap.chunksync.ChunkShareTransport.Host;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class ChunkShareConfig {
    private static final String FILE_NAME = "share.properties";
    private static final String KEY_PASSPHRASE = "passphrase";
    private static final String KEY_HOST = "host";

    private ChunkShareConfig() {
    }

    public static Path baseDir() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("voxelmap").resolve("chunk_share");
    }

    private static Path configFile() {
        return baseDir().resolve(FILE_NAME);
    }

    private static Properties load() {
        Properties props = new Properties();
        Path file = configFile();
        if (Files.isRegularFile(file)) {
            try (InputStream in = Files.newInputStream(file)) {
                props.load(in);
            } catch (IOException e) {
                VoxelConstants.getLogger().warn("Failed to read chunk-share config", e);
            }
        }
        return props;
    }

    private static void save(Properties props) {
        Path file = configFile();
        try {
            Files.createDirectories(file.getParent());
            try (OutputStream out = Files.newOutputStream(file)) {
                props.store(out, "VoxelMap chunk-share settings (passphrase is stored in plaintext)");
            }
        } catch (IOException e) {
            VoxelConstants.getLogger().warn("Failed to write chunk-share config", e);
        }
    }

    public static String getPassphrase() {
        String value = load().getProperty(KEY_PASSPHRASE);
        return value == null || value.isEmpty() ? null : value;
    }

    public static void setPassphrase(String passphrase) {
        Properties props = load();
        props.setProperty(KEY_PASSPHRASE, passphrase == null ? "" : passphrase);
        save(props);
    }

    /** the chosen host, defaulting to {@link Host#LITTERBOX}. */
    public static Host getHost() {
        return Host.fromId(load().getProperty(KEY_HOST));
    }

    public static void setHost(Host host) {
        Properties props = load();
        props.setProperty(KEY_HOST, host.id);
        save(props);
    }
}
