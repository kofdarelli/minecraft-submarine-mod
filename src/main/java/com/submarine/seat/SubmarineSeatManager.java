package com.submarine.seat;

import com.submarine.SubmarineMod;
import com.submarine.data.SubmarineMetadata;
import com.submarine.data.SubmarineSavedData;
import com.submarine.entity.ModEntities;
import com.submarine.entity.SubmarineSeatEntity;
import com.submarine.template.SeatSpec;
import com.submarine.template.SubmarineTemplate;
import com.submarine.template.SubmarineTemplates;
import java.util.ArrayList;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.AABB;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

public final class SubmarineSeatManager {
    private SubmarineSeatManager() {
    }

    public static void registerServerTicks() {
        ServerTickEvents.END_WORLD_TICK.register(level -> {
            if (level.getGameTime() % 200 != 0) {
                return;
            }
            String dimId = VSGameUtilsKt.getDimensionId(level);
            for (SubmarineMetadata metadata : new ArrayList<>(SubmarineSavedData.get(level).all())) {
                if (!metadata.dimensionId().equals(dimId)) {
                    continue;
                }
                if (VSGameUtilsKt.getShipObjectWorld(level).getAllShips().getById(metadata.shipId()) == null) {
                    discardOrphanedSeats(level, metadata);
                    SubmarineSavedData.get(level).remove(metadata.shipId());
                    continue;
                }
                ensureSeats(level, metadata);
            }
        });
    }

    public static InteractionResult tryUseSeat(ServerPlayer player, ServerLevel level, SubmarineMetadata metadata, BlockPos shipyardPos) {
        SubmarineTemplate template = SubmarineTemplates.get(metadata.templateId());
        BlockPos localPos = metadata.toLocal(shipyardPos);
        SeatSpec seatSpec = findSeatForClick(template, localPos);
        if (seatSpec == null) {
            SubmarineMod.LOGGER.info("[seat-debug] no seat spec near local {} (known seats: {})",
                    localPos, template.seats());
            return InteractionResult.PASS;
        }
        SubmarineMod.LOGGER.info("[seat-debug] matched seat index {} at local {}", seatSpec.index(), seatSpec.localPos());

        if (seatSpec.index() == 0 && !isAllowedPilot(player, metadata)) {
            player.displayClientMessage(Component.translatable("message.submarine.not_owner"), true);
            SubmarineMod.LOGGER.info("[seat-debug] denied: {} is not the owner/op", player.getGameProfile().getName());
            return InteractionResult.FAIL;
        }

        SubmarineSeatEntity seat = findOrCreateSeat(level, metadata, seatSpec);
        if (!seat.getPassengers().isEmpty()) {
            player.displayClientMessage(Component.translatable("message.submarine.seat_taken"), true);
            SubmarineMod.LOGGER.info("[seat-debug] seat {} already occupied", seat.getId());
            return InteractionResult.SUCCESS;
        }

        // Force the client to know about this entity before mounting — VS2's shipyard
        // may be outside the player's normal entity-tracking range (256 blocks now, but
        // still potentially far).  Sending the spawn + data packets directly ensures the
        // client has the entity in its registry before startRiding delivers the passenger
        // packet.
        player.connection.send(seat.getAddEntityPacket());
        var nonDefault = seat.getEntityData().getNonDefaultValues();
        if (nonDefault != null && !nonDefault.isEmpty()) {
            player.connection.send(new ClientboundSetEntityDataPacket(seat.getId(), nonDefault));
        }

        boolean mounted = player.startRiding(seat, true);
        SubmarineMod.LOGGER.info("[seat-debug] startRiding seat {} at {} -> {}", seat.getId(), seat.position(), mounted);
        if (!mounted) {
            return InteractionResult.FAIL;
        }
        return InteractionResult.SUCCESS;
    }

    public static void ensureSeats(ServerLevel level, SubmarineMetadata metadata) {
        SubmarineTemplate template = SubmarineTemplates.get(metadata.templateId());
        for (SeatSpec seat : template.seats()) {
            findOrCreateSeat(level, metadata, seat);
        }
    }

    private static void discardOrphanedSeats(ServerLevel level, SubmarineMetadata metadata) {
        SubmarineTemplate template = SubmarineTemplates.get(metadata.templateId());
        AABB searchBox = new AABB(metadata.shipyardOrigin()).inflate(template.searchRadius());
        for (SubmarineSeatEntity seat : new ArrayList<>(
                level.getEntities(ModEntities.SEAT, searchBox, s -> s.getShipId() == metadata.shipId()))) {
            seat.ejectPassengers();
            seat.discard();
        }
    }

    private static boolean isAllowedPilot(ServerPlayer player, SubmarineMetadata metadata) {
        return player.hasPermissions(2) || metadata.owner().equals(player.getUUID());
    }

    private static SeatSpec findSeatForClick(SubmarineTemplate template, BlockPos localPos) {
        SeatSpec exact = template.seatAt(localPos);
        if (exact != null) {
            return exact;
        }
        for (SeatSpec seat : template.seats()) {
            if (seat.localPos().distManhattan(localPos) <= 1) {
                return seat;
            }
        }
        return null;
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
