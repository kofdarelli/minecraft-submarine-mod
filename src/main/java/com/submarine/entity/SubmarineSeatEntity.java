package com.submarine.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class SubmarineSeatEntity extends Entity {
    private static final EntityDataAccessor<Integer> SEAT_INDEX =
            SynchedEntityData.defineId(SubmarineSeatEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> SHIP_ID =
            SynchedEntityData.defineId(SubmarineSeatEntity.class, EntityDataSerializers.LONG);

    public SubmarineSeatEntity(EntityType<? extends SubmarineSeatEntity> type, Level level) {
        super(type, level);
        this.blocksBuilding = false;
        this.noPhysics = true;
        setInvisible(true);
        setInvulnerable(true);
    }

    public int getSeatIndex() {
        return entityData.get(SEAT_INDEX);
    }

    public void setSeatIndex(int seatIndex) {
        entityData.set(SEAT_INDEX, seatIndex);
    }

    public long getShipId() {
        return entityData.get(SHIP_ID);
    }

    public void setShipId(long shipId) {
        entityData.set(SHIP_ID, shipId);
    }

    public boolean isPilotSeat() {
        return getSeatIndex() == 0;
    }

    @Override
    public void tick() {
        super.tick();
        this.noPhysics = true;
        setInvisible(true);
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return getPassengers().isEmpty();
    }

    @Override
    public LivingEntity getControllingPassenger() {
        if (!isPilotSeat() || getPassengers().isEmpty()) {
            return null;
        }
        Entity passenger = getPassengers().get(0);
        return passenger instanceof LivingEntity living ? living : null;
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        return passenger.position();
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(SEAT_INDEX, -1);
        entityData.define(SHIP_ID, -1L);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        setSeatIndex(tag.getInt("SeatIndex"));
        setShipId(tag.getLong("ShipId"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("SeatIndex", getSeatIndex());
        tag.putLong("ShipId", getShipId());
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this);
    }
}
