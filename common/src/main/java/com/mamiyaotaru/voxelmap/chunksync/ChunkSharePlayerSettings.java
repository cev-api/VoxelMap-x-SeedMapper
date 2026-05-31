package com.mamiyaotaru.voxelmap.chunksync;

import com.mamiyaotaru.voxelmap.VoxelConstants;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

public final class ChunkSharePlayerSettings {
    private static final String FILE_NAME = "players.properties";
    private static final String KEY_PLAYERS = "players";
    private static final int[] PALETTE = {
            0xFF5555, 0x55FF55, 0x5599FF, 0xFFEE44, 0xFF55FF,
            0x55FFFF, 0xFFAA33, 0xAA66FF, 0x33CCAA, 0xFF99CC
    };

    private ChunkSharePlayerSettings() {
    }

    public record PlayerLayer(String slug, boolean enabled, int rgb) {
    }

    public static synchronized List<PlayerLayer> list() {
        Properties props = load();
        Set<String> slugs = slugs(props);
        try {
            var voxelMap = VoxelConstants.getVoxelMapInstance();
            if (voxelMap != null) {
                boolean changed = slugs.addAll(voxelMap.getExploredChunksManager().playerLayerSlugs());
                changed |= slugs.addAll(voxelMap.getNewerNewChunksManager().playerLayerSlugs());
                if (changed) {
                    props.setProperty(KEY_PLAYERS, String.join(",", slugs));
                    save(props);
                }
            }
        } catch (RuntimeException ignored) {
            // Layer metadata can still be edited while no world is loaded.
        }
        List<PlayerLayer> layers = new ArrayList<>();
        for (String slug : slugs) {
            layers.add(new PlayerLayer(slug, enabled(props, slug), color(props, slug)));
        }
        return layers;
    }

    public static synchronized PlayerLayer get(String playerName) {
        String slug = ChunkShareService.slugFor(playerName);
        Properties props = load();
        return new PlayerLayer(slug, enabled(props, slug), color(props, slug));
    }

    public static synchronized void register(String playerName) {
        String slug = ChunkShareService.slugFor(playerName);
        Properties props = load();
        Set<String> slugs = slugs(props);
        if (slugs.add(slug)) {
            props.setProperty(KEY_PLAYERS, String.join(",", slugs));
            save(props);
        }
    }

    public static synchronized void remove(String playerName) {
        String slug = ChunkShareService.slugFor(playerName);
        Properties props = load();
        Set<String> slugs = slugs(props);
        slugs.remove(slug);
        props.setProperty(KEY_PLAYERS, String.join(",", slugs));
        props.remove(key(slug, "enabled"));
        props.remove(key(slug, "color"));
        save(props);
    }

    public static synchronized boolean toggleEnabled(String playerName) {
        String slug = ChunkShareService.slugFor(playerName);
        register(slug);
        Properties props = load();
        boolean enabled = !enabled(props, slug);
        props.setProperty(key(slug, "enabled"), Boolean.toString(enabled));
        save(props);
        return enabled;
    }

    public static synchronized void setColor(String playerName, int rgb) {
        String slug = ChunkShareService.slugFor(playerName);
        register(slug);
        Properties props = load();
        props.setProperty(key(slug, "color"), String.format("#%06X", rgb & 0x00FFFFFF));
        save(props);
    }

    public static synchronized void cycleColor(String playerName) {
        String slug = ChunkShareService.slugFor(playerName);
        int current = get(slug).rgb();
        int next = PALETTE[0];
        for (int i = 0; i < PALETTE.length; i++) {
            if (PALETTE[i] == current) {
                next = PALETTE[(i + 1) % PALETTE.length];
                break;
            }
        }
        setColor(slug, next);
    }

    public static int colorFor(String playerName, int alphaSource) {
        return (alphaSource & 0xFF000000) | get(playerName).rgb();
    }

    public static boolean isEnabled(String playerName) {
        return get(playerName).enabled();
    }

    private static Path file() {
        return ChunkShareConfig.baseDir().resolve(FILE_NAME);
    }

    private static Properties load() {
        Properties props = new Properties();
        if (Files.isRegularFile(file())) {
            try (InputStream in = Files.newInputStream(file())) {
                props.load(in);
            } catch (IOException e) {
                VoxelConstants.getLogger().warn("Failed to read chunk-share player settings", e);
            }
        }
        return props;
    }

    private static void save(Properties props) {
        try {
            Files.createDirectories(file().getParent());
            try (OutputStream out = Files.newOutputStream(file())) {
                props.store(out, "VoxelMap imported chunk-share player layers");
            }
        } catch (IOException e) {
            VoxelConstants.getLogger().warn("Failed to write chunk-share player settings", e);
        }
    }

    private static Set<String> slugs(Properties props) {
        Set<String> slugs = new LinkedHashSet<>();
        for (String slug : props.getProperty(KEY_PLAYERS, "").split(",")) {
            if (!slug.isBlank()) {
                slugs.add(slug.trim());
            }
        }
        return slugs;
    }

    private static boolean enabled(Properties props, String slug) {
        return Boolean.parseBoolean(props.getProperty(key(slug, "enabled"), "true"));
    }

    private static int color(Properties props, String slug) {
        String value = props.getProperty(key(slug, "color"));
        if (value != null && value.matches("#[0-9a-fA-F]{6}")) {
            return Integer.parseInt(value.substring(1), 16);
        }
        return PALETTE[Math.floorMod(slug.hashCode(), PALETTE.length)];
    }

    private static String key(String slug, String setting) {
        return "player." + slug + "." + setting;
    }
}
