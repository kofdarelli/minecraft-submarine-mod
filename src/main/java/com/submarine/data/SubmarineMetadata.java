package com.submarine.data;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

public final class SubmarineMetadata {
    private final long shipId;
    private final UUID owner;
    private final String templateId;
    private final String dimensionId;
    private final BlockPos shipyardOrigin;

    public SubmarineMetadata(long shipId, UUID owner, String templateId, String dimensionId, BlockPos shipyardOrigin) {
        this.shipId = shipId;
        this.owner = owner;
        this.templateId = templateId;
        this.dimensionId = dimensionId;
        this.shipyardOrigin = shipyardOrigin;
    }

    public long shipId() {
        return shipId;
    }

    public UUID owner() {
        return owner;
    }

    public String templateId() {
        return templateId;
    }

    public String dimensionId() {
        return dimensionId;
    }

    public BlockPos shipyardOrigin() {
        return shipyardOrigin;
    }

    public BlockPos toLocal(BlockPos shipyardPos) {
        return shipyardPos.subtract(shipyardOrigin);
    }

    public BlockPos toShipyard(BlockPos localPos) {
        return shipyardOrigin.offset(localPos);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("ShipId", shipId);
        tag.putUUID("Owner", owner);
        tag.putString("TemplateId", templateId);
        tag.putString("DimensionId", dimensionId);
        tag.putInt("OriginX", shipyardOrigin.getX());
        tag.putInt("OriginY", shipyardOrigin.getY());
        tag.putInt("OriginZ", shipyardOrigin.getZ());
        return tag;
    }

    public SubmarineMetadata withOwner(UUID newOwner) {
        return new SubmarineMetadata(shipId, newOwner, templateId, dimensionId, shipyardOrigin);
    }

    public static SubmarineMetadata load(CompoundTag tag) {
        return new SubmarineMetadata(
                tag.getLong("ShipId"),
                tag.getUUID("Owner"),
                tag.getString("TemplateId"),
                tag.getString("DimensionId"),
                new BlockPos(tag.getInt("OriginX"), tag.getInt("OriginY"), tag.getInt("OriginZ"))
        );
    }
}
