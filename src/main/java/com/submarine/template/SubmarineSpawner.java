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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3d;
import org.valkyrienskies.core.api.ships.ServerShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.assembly.ShipAssembler;

public final class SubmarineSpawner {
    private SubmarineSpawner() {
    }

    public static ServerShip spawnStarterSub(ServerLevel level, Player owner, BlockPos requestedOrigin) {
        return spawnSubmarine(level, owner, requestedOrigin, StarterSubmarineTemplate.TEMPLATE);
    }

    public static ServerShip spawnOceanPearl(ServerLevel level, Player owner, BlockPos requestedOrigin) {
        return spawnSubmarine(level, owner, requestedOrigin, OceanPearlSubmarineBuilder.TEMPLATE);
    }

    /** Builds, ships and registers any {@link SubmarineTemplate} the same way, regardless of its shape. */
    public static ServerShip spawnSubmarine(ServerLevel level, Player owner, BlockPos requestedOrigin, SubmarineTemplate template) {
        BlockPos origin = requestedOrigin;
        Map<BlockPos, BlockState> localBlocks = template.buildBlocks();
        Set<BlockPos> blockSet = new HashSet<>();
        BlockPos min = template.minLocal();
        BlockPos max = template.maxLocal();

        // Evict water from the full bounding box before placing blocks. Without this,
        // fluid updates triggered by the first hull blocks cause water to pour into
        // every gap that hasn't been filled yet, waterlogging the trapdoor, ladder,
        // and stairs in the process.
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int y = min.getY(); y <= max.getY(); y++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    BlockPos pos = origin.offset(x, y, z);
                    if (!level.getFluidState(pos).isEmpty()) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                    }
                }
            }
        }

        // Place blocks with UPDATE_KNOWN_SHAPE (16) so neighboring fluid states
        // cannot waterlog the newly placed blocks during the placement loop.
        for (Map.Entry<BlockPos, BlockState> entry : localBlocks.entrySet()) {
            BlockPos worldPos = origin.offset(entry.getKey());
            level.setBlock(worldPos, entry.getValue(), 2 | 16);
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
                template.id(),
                VSGameUtilsKt.getDimensionId(level),
                shipyardOrigin
        );
        SubmarineSavedData.get(level).put(metadata);

        // The VS shipyard may be in an ocean chunk, leaving water in any interior
        // position that isn't occupied by a hull or furniture block. Clear it now.
        for (BlockPos local : template.allLocalPositions()) {
            if (!localBlocks.containsKey(local)) {
                BlockPos shipyardPos = metadata.toShipyard(local);
                if (!level.getFluidState(shipyardPos).isEmpty()) {
                    level.setBlock(shipyardPos, Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }

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
