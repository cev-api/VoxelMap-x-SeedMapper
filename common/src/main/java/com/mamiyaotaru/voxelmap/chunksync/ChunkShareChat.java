package com.mamiyaotaru.voxelmap.chunksync;

import com.mamiyaotaru.voxelmap.VoxelConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Pattern;

/**
 * share over in-game chat as an encrypted token
 */
public final class ChunkShareChat {
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
    private static final Pattern CANDIDATE = Pattern.compile("[A-Za-z0-9_-]{80,400}");
    private static final int MAX_CANDIDATES_PER_MESSAGE = 3;
    private static final char SEP = '\n';

    private ChunkShareChat() {
    }

    public record ShareLink(String name, String url) {
    }

    public static String encode(String senderName, String url, String passphrase) {
        try {
            String plain = (senderName == null ? "" : senderName) + SEP + url;
            byte[] blob = ChunkShareCrypto.encrypt(plain.getBytes(StandardCharsets.UTF_8), passphrase, ChunkShareCrypto.CHAT_ITERATIONS);
            return ENCODER.encodeToString(blob);
        } catch (Exception e) {
            VoxelConstants.getLogger().warn("Failed to encode chunk-share chat token", e);
            return null;
        }
    }

    public static ShareLink tryDecode(String token, String passphrase) {
        byte[] blob;
        try {
            blob = DECODER.decode(token);
        } catch (IllegalArgumentException notBase64) {
            return null;
        }
        byte[] plain;
        try {
            plain = ChunkShareCrypto.decrypt(blob, passphrase);
        } catch (Exception wrongKeyOrNotAShare) {
            return null;
        }
        String text = new String(plain, StandardCharsets.UTF_8);
        int sep = text.indexOf(SEP);
        if (sep < 0) {
            return null;
        }
        String name = text.substring(0, sep);
        String url = text.substring(sep + 1).trim();
        if (!url.startsWith("http")) {
            return null;
        }
        return new ShareLink(name, url);
    }

    public static Component maybeResolveIncoming(Component message) {
        String passphrase = ChunkShareConfig.getPassphrase();
        if (passphrase == null) {
            return null;
        }
        String raw = message.getString();
        var matcher = CANDIDATE.matcher(raw);
        int attempts = 0;
        while (matcher.find() && attempts < MAX_CANDIDATES_PER_MESSAGE) {
            attempts++;
            ShareLink link = tryDecode(matcher.group(), passphrase);
            if (link != null) {
                return buildPrompt(link);
            }
        }
        return null;
    }

    private static Component buildPrompt(ShareLink link) {
        String name = link.name() == null || link.name().isBlank() ? "Someone" : link.name();
        if (name.equalsIgnoreCase(selfName())) {
            return Component.literal("📦 ")
                    .append(Component.literal("You shared your chunks").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(" — friends with the key can one-tap import.").withStyle(ChatFormatting.DARK_GRAY));
        }

        String command = "/chunksync get " + link.url() + " as " + name;
        Component hover = Component.literal("Click to download " + name + "'s chunks and add them as a coloured layer.");
        MutableComponent button = Component.literal("[Click to import]").withStyle(style -> style
                .withClickEvent(new ClickEvent.RunCommand(command))
                .withHoverEvent(new HoverEvent.ShowText(hover))
                .withColor(ChatFormatting.GREEN)
                .withBold(true));

        return Component.literal("📦 ")
                .append(Component.literal(name).withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" shared chunks. ").withStyle(ChatFormatting.YELLOW))
                .append(button);
    }

    private static String selfName() {
        try {
            return Minecraft.getInstance().getUser().getName();
        } catch (RuntimeException e) {
            return "";
        }
    }
}
