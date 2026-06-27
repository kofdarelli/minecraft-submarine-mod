package com.submarine.entity;

import com.submarine.SubmarineMod;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class ModEntities {
    public static final EntityType<SubmarineSeatEntity> SEAT = FabricEntityTypeBuilder
            .<SubmarineSeatEntity>create(MobCategory.MISC, SubmarineSeatEntity::new)
            .dimensions(EntityDimensions.fixed(0.001F, 0.001F))
            .trackRangeBlocks(96)
            .trackedUpdateRate(1)
            .forceTrackedVelocityUpdates(false)
            .build();

    private ModEntities() {
    }

    public static void register() {
        Registry.register(BuiltInRegistries.ENTITY_TYPE, new ResourceLocation(SubmarineMod.MOD_ID, "seat"), SEAT);
    }
}
