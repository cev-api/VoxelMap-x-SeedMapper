package com.mamiyaotaru.voxelmap.persistent;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ServerSeedStoreTest {
    @TempDir Path directory;
    @Test void editsAndDeletesPreserveWaypointsCommentsAndCrLf() throws Exception {
        Path file = directory.resolve("server.points");
        String before = "subworlds:realm~colon~1,\r\nseeds:all#123,realm~colon~1#-456,\r\n# comment\r\nname:日本語,x:7,z:-8,y:64,enabled:true\r\n";
        Files.writeString(file, before);
        ServerSeedStore.update(file, "all", "9223372036854775807");
        assertEquals(before.replace("all#123", "all#9223372036854775807"), Files.readString(file));
        ServerSeedStore.update(file, "realm~colon~1", "");
        assertEquals(before.replace("all#123", "all#9223372036854775807").replace("realm~colon~1#-456,", ""), Files.readString(file));
        assertEquals("9223372036854775807", ServerSeedStore.read(file).get("all"));
    }
    @Test void addingASeedHeaderPreservesAllExistingLines() throws Exception {
        Path file = directory.resolve("no-seeds.points");
        String waypoints = "# comment\nname:Spawn,x:0,z:0,y:64\n";
        Files.writeString(file, waypoints); ServerSeedStore.update(file, "all", "42");
        assertEquals("seeds:all#42,\n" + waypoints, Files.readString(file));
        ServerSeedStore.update(file, "all", "");
        assertEquals("seeds:\n" + waypoints, Files.readString(file));
    }
    @Test void savedServerWithoutExistingVoxelMapDataCanReceiveASeed() throws Exception {
        Path file = directory.resolve("new-server.points");
        ServerSeedStore.update(file, "all", "-17");
        assertEquals("-17", ServerSeedStore.read(file).get("all"));
    }
    @Test void invalidInputCannotRewriteWaypointData() throws Exception {
        Path file = directory.resolve("server.points"); Files.writeString(file, "seeds:all#123,\nname:Spawn\n");
        byte[] original = Files.readAllBytes(file);
        assertThrows(IllegalArgumentException.class, () -> ServerSeedStore.update(file, "all", "42\nname:Injected"));
        assertThrows(IllegalArgumentException.class, () -> ServerSeedStore.update(file, "all", "42,other#43"));
        assertArrayEquals(original, Files.readAllBytes(file));
    }
}
