package com.submarine.item;

import com.submarine.SubmarineMod;
import com.submarine.template.SubmarineSpawner;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class ModItems {
    public static final ResourceKey<CreativeModeTab> TAB_KEY = ResourceKey.create(
            Registries.CREATIVE_MODE_TAB, id("submarine"));

    public static final Item SUBMARINE_BLUEPRINT = new SubmarineBlueprintItem(
            new Item.Properties().stacksTo(1), SubmarineSpawner::spawnStarterSub);
    public static final Item OCEAN_PEARL_BLUEPRINT = new SubmarineBlueprintItem(
            new Item.Properties().stacksTo(1), SubmarineSpawner::spawnOceanPearl);

    private ModItems() {
    }

    public static void register() {
        Registry.register(BuiltInRegistries.ITEM, id("submarine_blueprint"), SUBMARINE_BLUEPRINT);
        Registry.register(BuiltInRegistries.ITEM, id("ocean_pearl_blueprint"), OCEAN_PEARL_BLUEPRINT);
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, TAB_KEY, FabricItemGroup.builder()
                .title(Component.translatable("itemGroup.submarine.submarine"))
                .icon(() -> new ItemStack(Items.HEART_OF_THE_SEA))
                .displayItems((parameters, output) -> {
                    output.accept(SUBMARINE_BLUEPRINT);
                    output.accept(OCEAN_PEARL_BLUEPRINT);
                })
                .build());
        ItemGroupEvents.modifyEntriesEvent(TAB_KEY).register(entries -> entries.accept(SUBMARINE_BLUEPRINT));
        ItemGroupEvents.modifyEntriesEvent(TAB_KEY).register(entries -> entries.accept(OCEAN_PEARL_BLUEPRINT));
    }

    public static boolean isBlueprint(Item item) {
        return item == SUBMARINE_BLUEPRINT || item == OCEAN_PEARL_BLUEPRINT;
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation(SubmarineMod.MOD_ID, path);
    }
}
