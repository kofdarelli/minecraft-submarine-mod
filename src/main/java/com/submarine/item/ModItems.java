package com.submarine.item;

import com.submarine.SubmarineMod;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class ModItems {
    public static final ResourceKey<CreativeModeTab> TAB_KEY = ResourceKey.create(
            Registries.CREATIVE_MODE_TAB, id("submarine"));

    public static final Item SUBMARINE_BLUEPRINT = new SubmarineBlueprintItem(
            new Item.Properties().stacksTo(1));

    private ModItems() {
    }

    public static void register() {
        Registry.register(BuiltInRegistries.ITEM, id("submarine_blueprint"), SUBMARINE_BLUEPRINT);
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, TAB_KEY, FabricItemGroup.builder()
                .title(Component.translatable("itemGroup.submarine.submarine"))
                .icon(() -> new ItemStack(Items.HEART_OF_THE_SEA))
                .displayItems((parameters, output) -> output.accept(SUBMARINE_BLUEPRINT))
                .build());
        ItemGroupEvents.modifyEntriesEvent(TAB_KEY).register(entries -> entries.accept(SUBMARINE_BLUEPRINT));
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation(SubmarineMod.MOD_ID, path);
    }
}
