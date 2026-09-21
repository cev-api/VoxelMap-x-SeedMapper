package com.mamiyaotaru.voxelmap.mixins;

import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Retains generated loot-table metadata so SeedMapper can inspect it later. */
@Mixin(RandomizableContainer.class)
public interface MixinRandomizableContainerLootTable {
    @Inject(method = "unpackLootTable", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/RandomizableContainer;setLootTable(Lnet/minecraft/resources/ResourceKey;)V"))
    private void voxelmap$retainLootTableInfo(Player player, CallbackInfo callbackInfo) {
        if (!(this instanceof RandomizableContainerBlockEntity container)) {
            return;
        }

        CompoundTag lootData = new CompoundTag();
        Tag lootTable = Identifier.CODEC
                .encodeStart(NbtOps.INSTANCE, container.getLootTable().identifier())
                .getOrThrow();
        lootData.put(RandomizableContainer.LOOT_TABLE_TAG, lootTable);
        lootData.putLong(RandomizableContainer.LOOT_TABLE_SEED_TAG, container.getLootTableSeed());

        DataComponentMap components = DataComponentMap.builder()
                .addAll(container.components())
                .set(DataComponents.CUSTOM_DATA, CustomData.of(lootData))
                .build();
        container.setComponents(components);
    }
}
