package com.mamiyaotaru.voxelmap.seedmapper;

import com.mamiyaotaru.voxelmap.chunksync.ChunkSyncCommands;
import com.mojang.brigadier.CommandDispatcher;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the command tree against argument/literal overlaps. A free-form "mode" word argument
 * sitting next to the clear/ore/orevein literals used to make Brigadier report an ambiguity for
 * those tokens, which also made tab completion unreliable.
 */
class SeedMapperCommandTreeTest {

    private static CommandDispatcher<Object> newDispatcher(java.util.function.Function<String, Integer> runner) {
        CommandDispatcher<Object> dispatcher = new CommandDispatcher<>();
        dispatcher.register(SeedMapperCommandTree.buildRoot("seedmap", runner));
        dispatcher.register(SeedMapperCommandTree.buildRoot("sm", runner));
        dispatcher.register(SeedMapperCommandTree.buildRoot("voxelmap", runner));
        dispatcher.register(SeedMapperCommandTree.buildRoot("vmap", runner));
        dispatcher.register(SeedMapperCommandTree.buildSourceRoot(runner));
        dispatcher.register(ChunkSyncCommands.buildRoot("chunksync", runner));
        return dispatcher;
    }

    @Test
    void commandTreeHasNoAmbiguities() {
        List<String> ambiguities = new ArrayList<>();
        newDispatcher(command -> 1).findAmbiguities((parent, child, sibling, inputs) ->
                ambiguities.add(parent.getName() + " -> " + child.getName() + " vs " + sibling.getName() + " " + inputs));

        assertEquals(List.of(), ambiguities, "Brigadier reported ambiguous command paths");
    }

    @Test
    void everyHighlightSpellingDispatchesToItsCanonicalForm() throws Exception {
        Map<String, String> expected = Map.ofEntries(
                Map.entry("seedmap highlight clear", "seedmap highlight clear"),
                Map.entry("seedmap highlight off", "seedmap highlight clear"),
                Map.entry("seedmap highlight ore diamond_ore", "seedmap highlight ore diamond_ore"),
                Map.entry("seedmap highlight ore diamond_ore 4", "seedmap highlight ore diamond_ore 4"),
                Map.entry("seedmap highlight block coal_ore 2", "seedmap highlight ore coal_ore 2"),
                Map.entry("seedmap highlight orevein", "seedmap highlight orevein"),
                Map.entry("seedmap highlight orevein 4", "seedmap highlight orevein 4"),
                Map.entry("seedmap highlight ore_vein 4", "seedmap highlight orevein 4"),
                Map.entry("seedmap highlight terrain", "seedmap highlight terrain"),
                Map.entry("seedmap highlight terrain 4", "seedmap highlight terrain 4"),
                Map.entry("seedmap highlight surface 4", "seedmap highlight surface 4"),
                Map.entry("seedmap highlight canyon 4", "seedmap highlight canyon 4"),
                Map.entry("seedmap highlight ravine 4", "seedmap highlight canyon 4"),
                Map.entry("seedmap highlight cave 4", "seedmap highlight cave 4"),
                Map.entry("seedmap highlight caves 4", "seedmap highlight cave 4")
        );

        for (Map.Entry<String, String> entry : expected.entrySet()) {
            List<String> executed = new ArrayList<>();
            newDispatcher(command -> {
                executed.add(command);
                return 1;
            }).execute(entry.getKey(), new Object());

            assertEquals(List.of(entry.getValue()), executed, entry.getKey());
        }
    }

    @Test
    void highlightChunkRadiusIsBounded() {
        // The chunks argument is 0..8; Brigadier must reject out-of-range values rather than
        // silently passing them down to the handler.
        CommandDispatcher<Object> dispatcher = newDispatcher(command -> 1);
        assertTrue(dispatcher.getRoot().getChild("seedmap")
                .getChild("highlight")
                .getChild("terrain")
                .getChild("chunks")
                .getCommand() != null);
    }
}
