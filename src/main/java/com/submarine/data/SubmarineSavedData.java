package com.submarine.data;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public final class SubmarineSavedData extends SavedData {
    private static final String ID = "submarine_submarines";

    private final Map<Long, SubmarineMetadata> submarines = new HashMap<>();

    public static SubmarineSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(SubmarineSavedData::load, SubmarineSavedData::new, ID);
    }

    public void put(SubmarineMetadata metadata) {
        submarines.put(metadata.shipId(), metadata);
        setDirty();
    }

    public Optional<SubmarineMetadata> get(long shipId) {
        return Optional.ofNullable(submarines.get(shipId));
    }

    public Collection<SubmarineMetadata> all() {
        return submarines.values();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (SubmarineMetadata metadata : submarines.values()) {
            list.add(metadata.save());
        }
        tag.put("Submarines", list);
        return tag;
    }

    private static SubmarineSavedData load(CompoundTag tag) {
        SubmarineSavedData data = new SubmarineSavedData();
        ListTag list = tag.getList("Submarines", 10);
        for (int i = 0; i < list.size(); i++) {
            SubmarineMetadata metadata = SubmarineMetadata.load(list.getCompound(i));
            data.submarines.put(metadata.shipId(), metadata);
        }
        return data;
    }
}
