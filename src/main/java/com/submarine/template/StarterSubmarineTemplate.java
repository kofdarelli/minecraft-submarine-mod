package com.submarine.template;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.StairsShape;
import org.joml.Vector3d;

/**
 * The starter submarine: a rounded, tapered hull built entirely from vanilla blocks.
 *
 * <p>Geometry is generated from an elliptical cross-section that narrows toward the bow
 * and stern ({@link #taper(int)}), giving a cigar-shaped silhouette rather than a box.
 * On top sit a conning tower (with beacon + access ladder) and a glass observation dome;
 * the bow carries a stained-glass cockpit canopy and the stern a spinning-look propeller.
 * The interior is split into a cockpit, storage hold, and engine room over a flat deck.
 */
public final class StarterSubmarineTemplate {
    public static final String ID = "starter_sub";
    public static final int LENGTH = 31; // x: bow (0) -> stern (30)
    public static final int HEIGHT = 12; // y: keel (0) -> superstructure
    public static final int WIDTH = 9;   // z: port (0) -> starboard (8)
    public static final BlockPos CENTER = new BlockPos(15, 4, 4);

    public static final double FORWARD_FORCE = 420000.0;
    public static final double VERTICAL_FORCE = 320000.0;
    public static final double YAW_TORQUE = 2200000.0;
    public static final double LINEAR_DRAG = 28000.0;
    public static final double ANGULAR_DRAG = 180000.0;
    public static final int IDLE_STATIC_AFTER_TICKS = 8;
    public static final double BUOYANCY_GRAVITY = 10.0;
    public static final double MIN_DEPTH = -60.0;

    // Hull geometry. The tube is centred at (cy, cz) with radius RADIUS * taper(x).
    private static final double CENTER_Y = 4.0;
    private static final double CENTER_Z = 4.0;
    private static final double RADIUS = 4.0;
    private static final int DECK_Y = 2;     // top surface of the walkable floor
    private static final int CABIN_Y = DECK_Y + 1; // where seats and furniture stand

    public static final List<SeatSpec> SEATS = List.of(
            new SeatSpec(0, new BlockPos(3, CABIN_Y, 4), Direction.WEST),  // pilot, in the cockpit
            new SeatSpec(1, new BlockPos(6, CABIN_Y, 2), Direction.WEST),  // co-pilot port
            new SeatSpec(2, new BlockPos(6, CABIN_Y, 6), Direction.WEST),  // co-pilot starboard
            new SeatSpec(3, new BlockPos(18, CABIN_Y, 2), Direction.WEST), // passenger port
            new SeatSpec(4, new BlockPos(18, CABIN_Y, 6), Direction.WEST)  // passenger starboard
    );

    // The cabin interior players may decorate; hull/furniture stay protected within it.
    public static final List<BlockBox> EDITABLE_BOXES = List.of(
            new BlockBox(new BlockPos(2, CABIN_Y, 2), new BlockPos(28, 7, 6))
    );

    private StarterSubmarineTemplate() {
    }

    public static Map<BlockPos, BlockState> buildBlocks() {
        Map<BlockPos, BlockState> blocks = new HashMap<>();

        buildHullAndDeck(blocks);
        buildConningTower(blocks);
        buildObservationDome(blocks);
        buildPropeller(blocks);
        buildInterior(blocks);
        putSeatBlocks(blocks);

        blocks.values().removeIf(BlockState::isAir);
        return blocks;
    }

    // --- geometry -----------------------------------------------------------

    /** Longitudinal taper in (0,1]: pinched at the bow and stern, full through midship. */
    private static double taper(int x) {
        if (x <= 5) {
            return 0.55 + 0.45 * (x / 5.0);
        }
        if (x >= 25) {
            return Math.max(0.45, 1.0 - 0.55 * ((x - 25) / 5.0));
        }
        return 1.0;
    }

    /** True if the elliptical hull cross-section at x contains (y, z). */
    private static boolean inHull(int x, int y, int z) {
        if (x < 0 || x >= LENGTH) {
            return false;
        }
        double radius = RADIUS * taper(x);
        double dy = (y - CENTER_Y) / radius;
        double dz = (z - CENTER_Z) / radius;
        return dy * dy + dz * dz <= 1.0 + 1.0e-9;
    }

    /** A hull cell is shell if any of its 6 neighbours falls outside the hull. */
    private static boolean isShell(int x, int y, int z) {
        return inHull(x, y, z) && !(inHull(x - 1, y, z) && inHull(x + 1, y, z)
                && inHull(x, y - 1, z) && inHull(x, y + 1, z)
                && inHull(x, y, z - 1) && inHull(x, y, z + 1));
    }

    private static void buildHullAndDeck(Map<BlockPos, BlockState> blocks) {
        BlockState hull = Blocks.DEEPSLATE_TILES.defaultBlockState();
        BlockState plating = Blocks.DARK_PRISMARINE.defaultBlockState();
        BlockState keel = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
        BlockState deck = Blocks.DEEPSLATE_BRICKS.defaultBlockState();
        BlockState ballast = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
        BlockState glass = Blocks.CYAN_STAINED_GLASS.defaultBlockState();

        for (int x = 0; x < LENGTH; x++) {
            for (int y = 0; y < HEIGHT; y++) {
                for (int z = 0; z < WIDTH; z++) {
                    if (!inHull(x, y, z)) {
                        continue;
                    }
                    if (isShell(x, y, z)) {
                        if (isWindow(x, y, z)) {
                            blocks.put(local(x, y, z), glass);
                        } else if (y <= 1) {
                            blocks.put(local(x, y, z), keel);
                        } else if (x % 6 == 0) {
                            blocks.put(local(x, y, z), plating);
                        } else {
                            blocks.put(local(x, y, z), hull);
                        }
                    } else if (y < DECK_Y) {
                        blocks.put(local(x, y, z), ballast);
                    } else if (y == DECK_Y) {
                        blocks.put(local(x, y, z), deck);
                    }
                    // y > DECK_Y interior cells are left open (air).
                }
            }
        }
    }

    /** Cockpit canopy at the bow, big viewing windows aft, and portholes along the flanks. */
    private static boolean isWindow(int x, int y, int z) {
        boolean canopy = x <= 4 && y >= 4 && z >= 2 && z <= 6;
        boolean viewingWindow = x >= 22 && x <= 27 && y >= 3 && y <= 5 && (z <= 1 || z >= WIDTH - 2);
        boolean porthole = y == 4 && (z == 0 || z == WIDTH - 1) && x >= 6 && x <= 20 && x % 4 == 0;
        return canopy || viewingWindow || porthole;
    }

    private static void buildConningTower(Map<BlockPos, BlockState> blocks) {
        BlockState wall = Blocks.DEEPSLATE_TILES.defaultBlockState();
        BlockState glass = Blocks.CYAN_STAINED_GLASS.defaultBlockState();

        // Carry a support pillar up the rear of the tower for the ladder to cling to.
        for (int y = CABIN_Y; y <= 10; y++) {
            blocks.put(local(15, y, 5), wall);
        }
        // Open a hatch through the roof shell so the ladder reaches the cabin. The roof
        // ridge is a single block wide here, so opening it exposes the north flank to the
        // sea; plug that flank with hull (the other three sides are already solid hull and
        // the ladder support pillar) to keep the penetration watertight.
        blocks.put(local(15, 8, 4), Blocks.AIR.defaultBlockState());
        blocks.put(local(15, 8, 3), wall);
        for (int y = CABIN_Y; y <= 10; y++) {
            blocks.put(local(15, y, 4), Blocks.LADDER.defaultBlockState()
                    .setValue(LadderBlock.FACING, Direction.NORTH));
        }

        // Tower walls (two storeys) around a 5x3 footprint.
        for (int x = 13; x <= 17; x++) {
            for (int z = 3; z <= 5; z++) {
                boolean perimeter = x == 13 || x == 17 || z == 3 || z == 5;
                for (int y = 9; y <= 10; y++) {
                    if (perimeter && !blocks.containsKey(local(x, y, z))) {
                        blocks.put(local(x, y, z), wall);
                    }
                }
            }
        }
        // Forward viewing slit and roof with a beacon + access hatch.
        blocks.put(local(13, 9, 4), glass);
        blocks.put(local(13, 10, 4), glass);
        for (int x = 13; x <= 17; x++) {
            for (int z = 3; z <= 5; z++) {
                blocks.put(local(x, 11, z), wall);
            }
        }
        // Access hatch: a trapdoor directly above the top of the ladder (15, *, 4) so the
        // deck is actually connected to the cabin. The old layout capped the ladder with a
        // sea lantern here and put the trapdoor at (14, 11, 5) — on a solid roof block that
        // led nowhere, so the submarine could not be entered at all. Closed by default to
        // keep the hull watertight; open it to climb in.
        blocks.put(local(15, 11, 4), Blocks.OAK_TRAPDOOR.defaultBlockState()
                .setValue(TrapDoorBlock.HALF, Half.TOP)
                .setValue(TrapDoorBlock.FACING, Direction.NORTH));
        // Beacon glow set beside the hatch so it no longer caps the ladder.
        blocks.put(local(15, 11, 3), Blocks.SEA_LANTERN.defaultBlockState());
        // Bow spotlight on the tower face.
        blocks.put(local(12, 10, 4), Blocks.SEA_LANTERN.defaultBlockState());
    }

    private static void buildObservationDome(Map<BlockPos, BlockState> blocks) {
        BlockState dome = Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState();
        // Glass ring on the aft roof with a capping pane, open to the cabin below.
        for (int x = 22; x <= 24; x++) {
            for (int z = 3; z <= 5; z++) {
                boolean perimeter = x == 22 || x == 24 || z == 3 || z == 5;
                if (perimeter) {
                    blocks.put(local(x, 9, z), dome);
                }
            }
        }
        blocks.put(local(23, 10, 4), dome);
        // Glaze the roof under the dome instead of leaving it open: the single-wide ridge
        // hole exposed its flanks to the sea and flooded the cabin. Glass keeps the cabin
        // watertight while the sealed observation bubble above stays visible from inside.
        blocks.put(local(23, 8, 4), dome);
    }

    private static void buildPropeller(Map<BlockPos, BlockState> blocks) {
        BlockState bars = Blocks.IRON_BARS.defaultBlockState();
        BlockState fin = Blocks.DARK_PRISMARINE.defaultBlockState();
        // Hub and four blades mounted AFT of the hull (x = 31), one block past the solid
        // stern cap at x = 30. Previously these sat on the cap itself and the iron-bar
        // blades replaced four of the five stern shell blocks, leaving the back of the hull
        // open to the sea — the cabin flooded straight through them. Keeping the propeller
        // external leaves x = 30 a watertight cap.
        blocks.put(local(31, 4, 4), Blocks.DEEPSLATE_TILES.defaultBlockState());
        blocks.put(local(31, 4, 3), bars);
        blocks.put(local(31, 4, 5), bars);
        blocks.put(local(31, 3, 4), bars);
        blocks.put(local(31, 5, 4), bars);
        // Dorsal and ventral stabiliser fins (solid, on the hull shell).
        blocks.put(local(29, 6, 4), fin);
        blocks.put(local(29, 2, 4), fin);
    }

    private static void buildInterior(Map<BlockPos, BlockState> blocks) {
        // Floor lighting strip down the centreline.
        for (int x : new int[]{5, 9, 15, 21, 27}) {
            blocks.put(local(x, DECK_Y, 4), Blocks.SEA_LANTERN.defaultBlockState());
        }

        // Storage hold (amidships): chests and barrels.
        blocks.put(local(9, CABIN_Y, 2), Blocks.CHEST.defaultBlockState()
                .setValue(ChestBlock.FACING, Direction.SOUTH)
                .setValue(ChestBlock.TYPE, ChestType.SINGLE));
        blocks.put(local(9, CABIN_Y, 6), Blocks.CHEST.defaultBlockState()
                .setValue(ChestBlock.FACING, Direction.NORTH)
                .setValue(ChestBlock.TYPE, ChestType.SINGLE));
        blocks.put(local(10, CABIN_Y, 2), Blocks.BARREL.defaultBlockState());
        blocks.put(local(10, CABIN_Y, 6), Blocks.BARREL.defaultBlockState());
        blocks.put(local(11, CABIN_Y, 2), Blocks.BARREL.defaultBlockState());

        // Engine room (aft): furnaces give the reactor glow.
        blocks.put(local(20, CABIN_Y, 2), Blocks.BLAST_FURNACE.defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH));
        blocks.put(local(20, CABIN_Y, 6), Blocks.BLAST_FURNACE.defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
        blocks.put(local(22, CABIN_Y, 4), Blocks.FURNACE.defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.WEST));

        // Cockpit work surfaces.
        blocks.put(local(5, CABIN_Y, 6), Blocks.CRAFTING_TABLE.defaultBlockState());
    }

    // --- queries used by the rest of the mod --------------------------------

    // The hull layout never changes at runtime, but protectedLocalPositions() is consulted on every
    // block break/place near the ship, so the geometry pass is cached after the first call.
    private static volatile Set<Long> protectedPositionsCache;

    public static Set<Long> protectedLocalPositions() {
        Set<Long> cached = protectedPositionsCache;
        if (cached == null) {
            Set<Long> built = new HashSet<>();
            buildBlocks().keySet().forEach(pos -> built.add(pos.asLong()));
            protectedPositionsCache = built;
            cached = built;
        }
        return cached;
    }

    public static boolean isEditable(BlockPos localPos) {
        return EDITABLE_BOXES.stream().anyMatch(box -> box.contains(localPos));
    }

    public static SeatSpec seatAt(BlockPos localPos) {
        for (SeatSpec seat : SEATS) {
            if (seat.localPos().equals(localPos)) {
                return seat;
            }
        }
        return null;
    }

    public static List<BlockPos> allLocalPositions() {
        List<BlockPos> positions = new ArrayList<>();
        for (int x = 0; x < LENGTH; x++) {
            for (int y = 0; y < HEIGHT; y++) {
                for (int z = 0; z < WIDTH; z++) {
                    positions.add(local(x, y, z));
                }
            }
        }
        return positions;
    }

    private static void putSeatBlocks(Map<BlockPos, BlockState> blocks) {
        for (SeatSpec seat : SEATS) {
            // SeatSpec.facing() is the direction the seated player looks (the pilot looks
            // WEST, toward the bow glass). A stair "chair" seats you facing its low, open
            // step, with the tall backrest behind — so the stair must face the OPPOSITE of
            // the view direction, otherwise the chair points away from the windows.
            blocks.put(seat.localPos(), Blocks.DARK_OAK_STAIRS.defaultBlockState()
                    .setValue(StairBlock.FACING, seat.facing().getOpposite())
                    .setValue(StairBlock.SHAPE, StairsShape.STRAIGHT));
        }
    }

    private static BlockPos local(int x, int y, int z) {
        return new BlockPos(x, y, z);
    }

    /** Adapts this class's static API to {@link SubmarineTemplate} for the generic spawn/control pipeline. */
    public static final SubmarineTemplate TEMPLATE = new SubmarineTemplate() {
        @Override
        public String id() {
            return ID;
        }

        @Override
        public BlockPos minLocal() {
            return BlockPos.ZERO;
        }

        @Override
        public BlockPos maxLocal() {
            return new BlockPos(LENGTH - 1, HEIGHT - 1, WIDTH - 1);
        }

        @Override
        public Map<BlockPos, BlockState> buildBlocks() {
            return StarterSubmarineTemplate.buildBlocks();
        }

        @Override
        public List<SeatSpec> seats() {
            return SEATS;
        }

        @Override
        public SeatSpec seatAt(BlockPos localPos) {
            return StarterSubmarineTemplate.seatAt(localPos);
        }

        @Override
        public boolean isEditable(BlockPos localPos) {
            return StarterSubmarineTemplate.isEditable(localPos);
        }

        @Override
        public Set<Long> protectedLocalPositions() {
            return StarterSubmarineTemplate.protectedLocalPositions();
        }

        @Override
        public double forwardForce() {
            return FORWARD_FORCE;
        }

        @Override
        public double verticalForce() {
            return VERTICAL_FORCE;
        }

        @Override
        public double yawTorque() {
            return YAW_TORQUE;
        }

        @Override
        public double linearDrag() {
            return LINEAR_DRAG;
        }

        @Override
        public double angularDrag() {
            return ANGULAR_DRAG;
        }

        @Override
        public int idleStaticAfterTicks() {
            return IDLE_STATIC_AFTER_TICKS;
        }

        @Override
        public double buoyancyGravity() {
            return BUOYANCY_GRAVITY;
        }

        @Override
        public double minDepth() {
            return MIN_DEPTH;
        }

        @Override
        public Vector3d localForward() {
            // Bow is at x = 0, stern at x = LENGTH - 1, so the ship's forward direction is -X.
            return new Vector3d(-1.0, 0.0, 0.0);
        }
    };
}
