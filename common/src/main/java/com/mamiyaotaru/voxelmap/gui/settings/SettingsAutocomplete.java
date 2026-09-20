package com.mamiyaotaru.voxelmap.gui.settings;

import de.voxelmap.voxelconfig.SettingsOption;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Fork-specific completion metadata without duplicating VoxelConfig's option model. */
final class SettingsAutocomplete {
    private static final Map<SettingsOption<?>, List<String>> OPTIONS = new WeakHashMap<>();

    private SettingsAutocomplete() {}

    static <T> SettingsOption<T> withOptions(SettingsOption<T> option, List<String> values) {
        OPTIONS.put(option, List.copyOf(values));
        return option;
    }

    static List<String> options(SettingsOption<?> option) {
        return OPTIONS.getOrDefault(option, List.of());
    }
}
