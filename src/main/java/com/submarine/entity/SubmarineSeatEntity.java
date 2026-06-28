package com.submarine.entity;

import com.submarine.data.SubmarineMetadata;
import com.submarine.data.SubmarineSavedData;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
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
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!getPassengers().isEmpty()) {
            player.displayClientMessage(Component.translatable("message.submarine.seat_taken"), true);
            return InteractionResult.SUCCESS;
        }
        if (isPilotSeat() && level() instanceof ServerLevel serverLevel) {
            Optional<SubmarineMetadata> meta = SubmarineSavedData.get(serverLevel).get(getShipId());
            if (meta.isPresent() && !meta.get().owner().equals(player.getUUID()) && !player.hasPermissions(2)) {
                player.displayClientMessage(Component.translatable("message.submarine.not_owner"), true);
                return InteractionResult.FAIL;
            }
        }
        player.startRiding(this, true);
        return InteractionResult.SUCCESS;
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        return position().add(0.0, 1.5, 0.0);
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
