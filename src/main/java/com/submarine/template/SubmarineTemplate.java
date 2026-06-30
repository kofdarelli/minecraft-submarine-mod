package com.submarine.template;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3d;

/**
 * Everything {@link com.submarine.template.SubmarineSpawner}, {@link com.submarine.control.SubmarineController},
 * {@link com.submarine.seat.SubmarineSeatManager} and {@link com.submarine.protection.SubmarineProtection} need to
 * turn a block layout into a pilotable, ownable, protected Valkyrien Skies ship. A template's blocks are always
 * authored in a fixed orientation; the mod does not currently rotate ships at spawn time (see
 * {@link #localForward()}).
 */
public interface SubmarineTemplate {
    /** Stable id stored in {@link com.submarine.data.SubmarineMetadata#templateId()}. */
    String id();

    /** Inclusive minimum corner of the template's local bounding box. */
    BlockPos minLocal();

    /** Inclusive maximum corner of the template's local bounding box. */
    BlockPos maxLocal();

    /** All blocks the template places, keyed by local position. */
    Map<BlockPos, BlockState> buildBlocks();

    /** Every seat the ship should have, including the pilot seat at index 0. */
    List<SeatSpec> seats();

    /** The seat (if any) whose block is at this local position. */
    SeatSpec seatAt(BlockPos localPos);

    /** True if players may break/place blocks at this local position (subject to {@link #protectedLocalPositions()}). */
    boolean isEditable(BlockPos localPos);

    /** Local positions of the template's own blocks; these stay protected even inside an editable zone. */
    Set<Long> protectedLocalPositions();

    double forwardForce();

    double verticalForce();

    double yawTorque();

    double linearDrag();

    double angularDrag();

    int idleStaticAfterTicks();

    double buoyancyGravity();

    double minDepth();

    /**
     * The unit direction (in the template's own authored axes) that points from the ship's centre toward the
     * bow. Because VS2 assembles a ship's local space directly from the world positions of its blocks at spawn
     * time, and this mod always places templates unrotated, this is also the bow direction in ship-local space
     * for the lifetime of the ship.
     */
    Vector3d localForward();

    /** Every local position inside the template's bounding box, used for area clearing/eviction. */
    default List<BlockPos> allLocalPositions() {
        List<BlockPos> positions = new ArrayList<>();
        BlockPos min = minLocal();
        BlockPos max = maxLocal();
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int y = min.getY(); y <= max.getY(); y++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    positions.add(new BlockPos(x, y, z));
                }
            }
        }
        return positions;
    }

    /** A horizontal radius big enough to bound the whole ship, for entity-search AABBs. */
    default int searchRadius() {
        BlockPos min = minLocal();
        BlockPos max = maxLocal();
        return Math.max(max.getX() - min.getX(), max.getZ() - min.getZ());
    }
}
