package com.mamiyaotaru.voxelmap.util;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public final class AppChatMessages {
    private AppChatMessages() {
    }

    public static MutableComponent prefixed(String app, String text) {
        return Component.literal("[" + app + "] ").withStyle(ChatFormatting.AQUA)
                .append(Component.literal(text).withStyle(ChatFormatting.WHITE));
    }
}
