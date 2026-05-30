package com.mamiyaotaru.voxelmap.persistent.explored;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

public final class ExploredContainerIo {
    private ExploredContainerIo() {
    }

    public static void write(Path file, ExploredContainer container) throws IOException {
        writeBytes(file, container.encode());
    }

    public static void writeBytes(Path file, byte[] data) throws IOException {
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.write(temp, data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        try {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicUnsupported) {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static ExploredContainer read(Path file) {
        byte[] data;
        try {
            data = Files.readAllBytes(file);
        } catch (IOException missingOrUnreadable) {
            return null;
        }
        try {
            return ExploredContainer.decode(data);
        } catch (IllegalArgumentException corrupt) {
            return null;
        }
    }
}
