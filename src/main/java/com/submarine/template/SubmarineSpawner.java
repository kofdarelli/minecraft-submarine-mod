package com.submarine.template;

import com.submarine.data.SubmarineMetadata;
import com.submarine.data.SubmarineSavedData;
import com.submarine.seat.SubmarineSeatManager;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3d;
import org.valkyrienskies.core.api.ships.ServerShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.assembly.ShipAssembler;

public final class SubmarineSpawner {
    private SubmarineSpawner() {
    }

    public static ServerShip spawnStarterSub(ServerLevel level, Player owner, BlockPos requestedOrigin) {
        BlockPos origin = requestedOrigin;
        Map<BlockPos, BlockState> localBlocks = StarterSubmarineTemplate.buildBlocks();
        Set<BlockPos> blockSet = new HashSet<>();

        for (Map.Entry<BlockPos, BlockState> entry : localBlocks.entrySet()) {
            BlockPos worldPos = origin.offset(entry.getKey());
            level.setBlock(worldPos, entry.getValue(), 3);
            blockSet.add(worldPos);
        }

        ShipAssembler.AssembleContext context = ShipAssembler.assembleToShipFull(level, blockSet, 1.0);
        ServerShip ship = context.getShip();
        ship.setStatic(false);

        Vector3d centerDelta = new Vector3d(context.getToCenter()).sub(context.getFromCenter());
        BlockPos shipyardOrigin = new BlockPos(
                (int) Math.round(origin.getX() + centerDelta.x),
                (int) Math.round(origin.getY() + centerDelta.y),
                (int) Math.round(origin.getZ() + centerDelta.z)
        );

        UUID ownerId = owner == null ? UtilUuid.ZERO : owner.getUUID();
        SubmarineMetadata metadata = new SubmarineMetadata(
                ship.getId(),
                ownerId,
                StarterSubmarineTemplate.ID,
                VSGameUtilsKt.getDimensionId(level),
                shipyardOrigin
        );
        SubmarineSavedData.get(level).put(metadata);
        SubmarineSeatManager.ensureSeats(level, metadata);

        if (owner != null) {
            owner.displayClientMessage(Component.translatable("command.submarine.spawned"), false);
        }
        return ship;
    }

    private static final class UtilUuid {
        private static final UUID ZERO = new UUID(0L, 0L);
    }
}
