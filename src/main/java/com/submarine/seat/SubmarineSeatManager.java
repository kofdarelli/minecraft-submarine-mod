package com.submarine.seat;

import com.submarine.data.SubmarineMetadata;
import com.submarine.data.SubmarineSavedData;
import com.submarine.entity.ModEntities;
import com.submarine.entity.SubmarineSeatEntity;
import com.submarine.template.SeatSpec;
import com.submarine.template.StarterSubmarineTemplate;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

public final class SubmarineSeatManager {
    private SubmarineSeatManager() {
    }

    public static void registerServerTicks() {
        ServerTickEvents.END_WORLD_TICK.register(level -> {
            if (level.getGameTime() % 200 != 0) {
                return;
            }
            for (SubmarineMetadata metadata : SubmarineSavedData.get(level).all()) {
                if (metadata.dimensionId().equals(org.valkyrienskies.mod.common.VSGameUtilsKt.getDimensionId(level))) {
                    ensureSeats(level, metadata);
                }
            }
        });
    }

    public static InteractionResult tryUseSeat(ServerPlayer player, ServerLevel level, SubmarineMetadata metadata, BlockPos shipyardPos) {
        SeatSpec seatSpec = StarterSubmarineTemplate.seatAt(metadata.toLocal(shipyardPos));
        if (seatSpec == null) {
            return InteractionResult.PASS;
        }

        SubmarineSeatEntity seat = findOrCreateSeat(level, metadata, seatSpec);
        if (!seat.getPassengers().isEmpty()) {
            player.displayClientMessage(Component.translatable("message.submarine.seat_taken"), true);
            return InteractionResult.SUCCESS;
        }

        player.startRiding(seat, true);
        return InteractionResult.SUCCESS;
    }

    public static void ensureSeats(ServerLevel level, SubmarineMetadata metadata) {
        for (SeatSpec seat : StarterSubmarineTemplate.SEATS) {
            findOrCreateSeat(level, metadata, seat);
        }
    }

    private static SubmarineSeatEntity findOrCreateSeat(ServerLevel level, SubmarineMetadata metadata, SeatSpec seatSpec) {
        BlockPos seatPos = metadata.toShipyard(seatSpec.localPos());
        AABB box = new AABB(seatPos).inflate(1.0);
        for (SubmarineSeatEntity existing : level.getEntities(ModEntities.SEAT, box,
                entity -> entity.getShipId() == metadata.shipId() && entity.getSeatIndex() == seatSpec.index())) {
            return existing;
        }

        SubmarineSeatEntity seat = new SubmarineSeatEntity(ModEntities.SEAT, level);
        seat.setShipId(metadata.shipId());
        seat.setSeatIndex(seatSpec.index());
        seat.setPos(seatPos.getX() + 0.5, seatPos.getY() + 0.25, seatPos.getZ() + 0.5);
        seat.setYRot(seatSpec.facing().toYRot());
        level.addFreshEntity(seat);
        return seat;
    }
}
