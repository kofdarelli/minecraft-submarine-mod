package com.submarine.template;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.StairsShape;
import org.joml.Vector3d;

/**
 * The Ocean Pearl: a 56x19x19 luxury cruise submarine built entirely from vanilla blocks.
 *
 * <p>Local coordinates follow the design spec directly: X is width (-9..9, centreline 0),
 * Y is height (0 keel .. 18 cupola), Z is length (-24 bow .. +31 stern). A single unified
 * hull volume ({@link #insideHull}) spans the bow dome, the dark-prismarine/quartz main
 * hull and the tapered rear engine pod, so the three sections open into one continuous,
 * walkable interior. Layered white decks, a glass cupola, glowing side windows and the
 * furnished interior rooms are all built as overlays on top of that shared hull shell.
 */
public final class OceanPearlSubmarineBuilder {
    public static final String ID = "ocean_pearl";
    public static final String NAME = "Ocean Pearl";
    public static final int LENGTH = 56;
    public static final int WIDTH = 19;
    public static final int HEIGHT = 19;

    public static final double FORWARD_FORCE = 1200000.0;
    public static final double VERTICAL_FORCE = 900000.0;
    public static final double YAW_TORQUE = 6000000.0;
    public static final double LINEAR_DRAG = 70000.0;
    public static final double ANGULAR_DRAG = 450000.0;
    public static final int IDLE_STATIC_AFTER_TICKS = 8;
    public static final double BUOYANCY_GRAVITY = 10.0;
    public static final double MIN_DEPTH = -60.0;

    // Pilot seat (index 0) and co-pilot seat sit at the cockpit console; the rest are
    // passenger seating on furniture the interior already places (lounge sofas, the
    // rear observation benches), so their positions/facings mirror those exactly.
    public static final List<SeatSpec> SEATS = List.of(
            new SeatSpec(0, new BlockPos(-2, 4, -19), Direction.NORTH),
            new SeatSpec(1, new BlockPos(2, 4, -19), Direction.NORTH),
            new SeatSpec(2, new BlockPos(-6, 5, -10), Direction.EAST),
            new SeatSpec(3, new BlockPos(6, 5, -10), Direction.WEST),
            new SeatSpec(4, new BlockPos(-2, 5, 24), Direction.SOUTH),
            new SeatSpec(5, new BlockPos(2, 5, 24), Direction.SOUTH)
    );

    // The main interior corridor/rooms players may decorate; hull, decks and furniture stay protected within it.
    public static final List<BlockBox> EDITABLE_BOXES = List.of(
            new BlockBox(new BlockPos(-6, 3, -22), new BlockPos(6, 9, 25))
    );

    private static final int MIN_X = -9;
    private static final int MAX_X = 9;
    private static final int MIN_Y = 0;
    private static final int HULL_MAX_Y = 11; // hull/dome/rear shell stops here; decks take over above

    private static final int BOW_Z = -24;
    private static final int DOME_END_Z = -15;
    private static final int HULL_START_Z = -14;
    private static final int HULL_END_Z = 24;
    private static final int STERN_START_Z = 25;
    private static final int STERN_Z = 31;

    private static final double DOME_CENTER_Y = 6.0;
    private static final int[] DOME_WIDTH = {5, 7, 9, 11, 13, 15, 17, 17, 17, 17};
    private static final int[] DOME_HEIGHT = {5, 6, 7, 8, 9, 10, 10, 10, 10, 10};

    private static final int[][] WINDOW_Z_RANGES = {{-12, -6}, {-4, 3}, {5, 12}, {14, 21}};

    private OceanPearlSubmarineBuilder() {
    }

    public static Map<BlockPos, BlockState> buildBlocks() {
        Map<BlockPos, BlockState> blocks = new HashMap<>();
        placeBowDome(blocks);
        placeHullSection(blocks);
        placeRearEngine(blocks);
        placeSideWindows(blocks);
        placeUpperDecks(blocks);
        placeGoldAccents(blocks);
        placeInterior(blocks);
        blocks.values().removeIf(BlockState::isAir);
        return blocks;
    }

    // --- shared hull geometry -------------------------------------------------

    /** True if (x,y,z) lies inside the ship's solid envelope: dome, main hull or rear taper. */
    private static boolean insideHull(int x, int y, int z) {
        if (z < BOW_Z || z > STERN_Z) {
            return false;
        }
        if (z <= DOME_END_Z) {
            return insideDomeBand(x, y, z);
        }
        if (z <= HULL_END_Z) {
            return insideMainBand(x, y, 1.0);
        }
        double t = (z - STERN_START_Z) / (double) (STERN_Z - STERN_START_Z);
        return insideMainBand(x, y, lerp(1.0, 0.4, t));
    }

    /** True if (x,y,z) is on the outer skin of the hull (at least one neighbour is outside). */
    private static boolean isHullShell(int x, int y, int z) {
        return insideHull(x, y, z) && !(insideHull(x - 1, y, z) && insideHull(x + 1, y, z)
                && insideHull(x, y - 1, z) && insideHull(x, y + 1, z)
                && insideHull(x, y, z - 1) && insideHull(x, y, z + 1));
    }

    private static boolean insideMainBand(int x, int y, double scale) {
        int half;
        if (y == 0) {
            half = 2;
        } else if (y <= 3) {
            half = 5;
        } else if (y <= 8) {
            half = 8;
        } else if (y <= HULL_MAX_Y) {
            half = 7;
        } else {
            return false;
        }
        int scaledHalf = (int) Math.round(half * scale);
        return Math.abs(x) <= scaledHalf;
    }

    private static boolean insideDomeBand(int x, int y, int z) {
        int index = z - BOW_Z;
        if (index < 0 || index >= DOME_WIDTH.length) {
            return false;
        }
        double radiusX = DOME_WIDTH[index] / 2.0;
        double radiusY = DOME_HEIGHT[index] / 2.0;
        double dx = x / radiusX;
        double dy = (y - DOME_CENTER_Y) / radiusY;
        return dx * dx + dy * dy <= 1.0 + 1.0e-9;
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    // --- section 1: bow glass observation dome (Z -24..-15) -------------------

    private static void placeBowDome(Map<BlockPos, BlockState> blocks) {
        BlockState domeGlass = Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState();
        BlockState rib = Blocks.GLASS_PANE.defaultBlockState();
        BlockState frame = Blocks.QUARTZ_BLOCK.defaultBlockState();
        BlockState headlight = Blocks.SEA_LANTERN.defaultBlockState();

        for (int z = BOW_Z; z <= DOME_END_Z; z++) {
            for (int x = MIN_X; x <= MAX_X; x++) {
                for (int y = MIN_Y; y <= HULL_MAX_Y; y++) {
                    if (!isHullShell(x, y, z)) {
                        continue;
                    }
                    boolean baseRing = !insideHull(x, y - 1, z);
                    if (baseRing && z >= -23 && z <= -20 && Math.abs(x) <= 2) {
                        blocks.put(local(x, y, z), headlight);
                    } else if (baseRing) {
                        blocks.put(local(x, y, z), frame);
                    } else if (Math.floorMod(x + z, 3) == 0) {
                        blocks.put(local(x, y, z), rib);
                    } else {
                        blocks.put(local(x, y, z), domeGlass);
                    }
                }
            }
        }
    }

    // --- sections 2/3: dark prismarine lower hull + quartz upper hull ---------

    private static void placeHullSection(Map<BlockPos, BlockState> blocks) {
        BlockState keel = Blocks.DARK_PRISMARINE.defaultBlockState();
        BlockState lowerHull = Blocks.DARK_PRISMARINE.defaultBlockState();
        BlockState mainHull = Blocks.PRISMARINE_BRICKS.defaultBlockState();
        BlockState upperHull = Blocks.SMOOTH_QUARTZ.defaultBlockState();
        BlockState bandTrim = Blocks.PRISMARINE.defaultBlockState();

        for (int z = HULL_START_Z; z <= HULL_END_Z; z++) {
            for (int x = MIN_X; x <= MAX_X; x++) {
                for (int y = MIN_Y; y <= HULL_MAX_Y; y++) {
                    if (!isHullShell(x, y, z)) {
                        continue;
                    }
                    BlockState material;
                    if (y == 0) {
                        material = keel;
                    } else if (y <= 3) {
                        material = lowerHull;
                    } else if (y <= 8) {
                        material = Math.floorMod(z, 8) == 0 ? bandTrim : mainHull;
                    } else {
                        material = upperHull;
                    }
                    blocks.put(local(x, y, z), material);
                }
            }
        }
    }

    // --- section 8: tapered rear engine / thruster pod (Z 25..31) -------------

    private static void placeRearEngine(Map<BlockPos, BlockState> blocks) {
        BlockState innerHull = Blocks.COPPER_BLOCK.defaultBlockState();
        BlockState midHull = Blocks.EXPOSED_COPPER.defaultBlockState();
        BlockState outerHull = Blocks.IRON_BLOCK.defaultBlockState();
        BlockState guard = Blocks.IRON_BARS.defaultBlockState();

        for (int z = STERN_START_Z; z <= STERN_Z; z++) {
            for (int x = MIN_X; x <= MAX_X; x++) {
                for (int y = MIN_Y; y <= HULL_MAX_Y; y++) {
                    if (!isHullShell(x, y, z)) {
                        continue;
                    }
                    BlockState material;
                    if (y <= 3) {
                        material = innerHull;
                    } else if (y <= 8) {
                        material = midHull;
                    } else {
                        material = outerHull;
                    }
                    blocks.put(local(x, y, z), material);
                }
            }
        }

        // Side stabiliser fins partway down the taper.
        BlockState finStair = Blocks.DARK_PRISMARINE_STAIRS.defaultBlockState()
                .setValue(StairBlock.SHAPE, StairsShape.STRAIGHT);
        putIfInside(blocks, -9, 6, 27, finStair.setValue(StairBlock.FACING, Direction.WEST));
        putIfInside(blocks, 9, 6, 27, finStair.setValue(StairBlock.FACING, Direction.EAST));
        putIfInside(blocks, -8, 6, 27, Blocks.DARK_PRISMARINE_SLAB.defaultBlockState());
        putIfInside(blocks, 8, 6, 27, Blocks.DARK_PRISMARINE_SLAB.defaultBlockState());

        // Thruster hub on the stern face: copper core, gold cap, ring of guard bars.
        int hubZ = STERN_Z;
        int[][] ringOffsets = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
        for (int[] offset : ringOffsets) {
            putIfInside(blocks, offset[0], 6 + offset[1], hubZ, guard);
        }
        putIfInside(blocks, 0, 6, hubZ, Blocks.GOLD_BLOCK.defaultBlockState());
        putIfInside(blocks, 0, 6, hubZ - 1, Blocks.LIGHTNING_ROD.defaultBlockState());
    }

    // --- section 5: long glowing side windows ----------------------------------

    private static void placeSideWindows(Map<BlockPos, BlockState> blocks) {
        BlockState glassWall = Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState();
        BlockState outerPane = Blocks.LIGHT_BLUE_STAINED_GLASS_PANE.defaultBlockState();
        BlockState glow = Blocks.SEA_LANTERN.defaultBlockState();
        BlockState border = Blocks.SMOOTH_QUARTZ.defaultBlockState();
        BlockState goldTrim = Blocks.GOLD_BLOCK.defaultBlockState();

        for (int[] range : WINDOW_Z_RANGES) {
            for (int side : new int[]{-1, 1}) {
                int hullX = side * 8;
                int outerX = side * 9;
                for (int z = range[0]; z <= range[1]; z++) {
                    for (int y = 6; y <= 8; y++) {
                        blocks.put(local(hullX, y, z), glassWall);
                        blocks.put(local(outerX, y, z), outerPane);
                    }
                    blocks.put(local(hullX - side, 7, z), glow);
                    blocks.put(local(hullX, 5, z), border);
                    blocks.put(local(hullX, 9, z), border);
                }
                blocks.put(local(hullX, 9, range[0]), goldTrim);
                blocks.put(local(hullX, 9, range[1]), goldTrim);
            }
        }
    }

    // --- gold and luxury accents ------------------------------------------------

    private static void placeGoldAccents(Map<BlockPos, BlockState> blocks) {
        for (int z = HULL_START_Z; z <= HULL_END_Z; z += 6) {
            for (int side : new int[]{-1, 1}) {
                int x = side * 8;
                if (isHullShell(x, 9, z)) {
                    blocks.put(local(x, 9, z), Blocks.GOLD_BLOCK.defaultBlockState());
                }
            }
        }

        // Small gold trim blocks along the first deck's outer rail line.
        BlockState gold = Blocks.GOLD_BLOCK.defaultBlockState();
        for (int z : new int[]{-8, 5, 18}) {
            blocks.put(local(-8, 12, z), gold);
            blocks.put(local(8, 12, z), gold);
        }
    }

    // --- sections 6/7: layered upper decks, bridge and glass cupola ------------

    private static void placeUpperDecks(Map<BlockPos, BlockState> blocks) {
        placeDeckFloor(blocks, 12, -7, 7, -8, 18);
        placeDeckRailing(blocks, 12, -7, 7, -8, 18);
        placeDeckFloor(blocks, 14, -5, 5, -3, 12);
        placeDeckRailing(blocks, 14, -5, 5, -3, 12);
        placeDeckFloor(blocks, 16, -3, 3, 1, 8);
        placeBridge(blocks);
        placeCupola(blocks);
    }

    private static void placeDeckFloor(Map<BlockPos, BlockState> blocks, int y, int xMin, int xMax, int zMin, int zMax) {
        BlockState quartz = Blocks.SMOOTH_QUARTZ.defaultBlockState();
        for (int x = xMin; x <= xMax; x++) {
            for (int z = zMin; z <= zMax; z++) {
                blocks.put(local(x, y, z), quartz);
            }
        }
        for (int x = xMin; x <= xMax; x++) {
            blocks.put(local(x, y, zMin - 1), stairFacing(Direction.NORTH));
            blocks.put(local(x, y, zMax + 1), stairFacing(Direction.SOUTH));
        }
        for (int z = zMin; z <= zMax; z++) {
            blocks.put(local(xMin - 1, y, z), stairFacing(Direction.WEST));
            blocks.put(local(xMax + 1, y, z), stairFacing(Direction.EAST));
        }
    }

    private static BlockState stairFacing(Direction facing) {
        return Blocks.SMOOTH_QUARTZ_STAIRS.defaultBlockState()
                .setValue(StairBlock.FACING, facing)
                .setValue(StairBlock.SHAPE, StairsShape.STRAIGHT);
    }

    private static void placeDeckRailing(Map<BlockPos, BlockState> blocks, int deckY, int xMin, int xMax, int zMin, int zMax) {
        BlockState bar = Blocks.IRON_BARS.defaultBlockState();
        BlockState post = Blocks.CHAIN.defaultBlockState();
        for (int x = xMin; x <= xMax; x++) {
            for (int z = zMin; z <= zMax; z++) {
                boolean edgeCell = x == xMin || x == xMax || z == zMin || z == zMax;
                if (!edgeCell) {
                    continue;
                }
                boolean corner = (x == xMin || x == xMax) && (z == zMin || z == zMax);
                blocks.put(local(x, deckY + 1, z), corner ? post : bar);
            }
        }

        BlockState gold = Blocks.GOLD_BLOCK.defaultBlockState();
        blocks.put(local(xMin, deckY, zMin), gold);
        blocks.put(local(xMax, deckY, zMin), gold);
        blocks.put(local(xMin, deckY, zMax), gold);
        blocks.put(local(xMax, deckY, zMax), gold);

        BlockState carpet = Blocks.YELLOW_CARPET.defaultBlockState();
        blocks.put(local(xMin + 1, deckY + 1, zMin + 1), carpet);
        blocks.put(local(xMax - 1, deckY + 1, zMin + 1), carpet);
        blocks.put(local(xMin + 1, deckY + 1, zMax - 1), carpet);
        blocks.put(local(xMax - 1, deckY + 1, zMax - 1), carpet);
    }

    /** The raised, glass-walled bridge deck near the top of the ship. */
    private static void placeBridge(Map<BlockPos, BlockState> blocks) {
        int xMin = -3;
        int xMax = 3;
        int zMin = 1;
        int zMax = 8;
        BlockState glass = Blocks.GLASS.defaultBlockState();
        BlockState pillar = Blocks.QUARTZ_PILLAR.defaultBlockState();
        BlockState roof = Blocks.SMOOTH_QUARTZ_SLAB.defaultBlockState();

        for (int x = xMin; x <= xMax; x++) {
            for (int z = zMin; z <= zMax; z++) {
                boolean edgeCell = x == xMin || x == xMax || z == zMin || z == zMax;
                if (!edgeCell) {
                    continue;
                }
                boolean corner = (x == xMin || x == xMax) && (z == zMin || z == zMax);
                blocks.put(local(x, 17, z), corner ? pillar : glass);
            }
        }

        for (int x = xMin; x <= xMax; x++) {
            for (int z = zMin; z <= zMax; z++) {
                boolean cupolaHole = Math.abs(x) <= 1 && z >= 3 && z <= 5;
                if (!cupolaHole) {
                    blocks.put(local(x, 18, z), roof);
                }
            }
        }
        blocks.put(local(0, 17, 4), Blocks.LANTERN.defaultBlockState());
    }

    /** The central glass cupola on top of the bridge, the ship's signature rooftop feature. */
    private static void placeCupola(Map<BlockPos, BlockState> blocks) {
        BlockState glass = Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState();
        BlockState rib = Blocks.GLASS_PANE.defaultBlockState();
        int centerZ = 4;

        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist > 3.4 || dist < 2.6) {
                    continue;
                }
                BlockState material = Math.floorMod(dx + dz, 2) == 0 ? glass : rib;
                blocks.put(local(dx, 17, centerZ + dz), material);
            }
        }

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                blocks.put(local(dx, 18, centerZ + dz), glass);
            }
        }
        blocks.put(local(0, 18, centerZ), Blocks.GOLD_BLOCK.defaultBlockState());
        blocks.put(local(0, 16, centerZ), Blocks.SEA_LANTERN.defaultBlockState());
    }

    // --- interior: cockpit, lounge, cabins, dining, engine room, observation ---

    private static void placeInterior(Map<BlockPos, BlockState> blocks) {
        placeCockpit(blocks);
        placeLounge(blocks);
        placeCabins(blocks);
        placeDiningNook(blocks);
        placeEngineRoom(blocks);
        placeRearObservation(blocks);
    }

    private static void placeCockpit(Map<BlockPos, BlockState> blocks) {
        floorRect(blocks, 3, -6, 6, -23, -15, Blocks.SMOOTH_QUARTZ.defaultBlockState());

        BlockState seat = Blocks.SPRUCE_STAIRS.defaultBlockState()
                .setValue(StairBlock.FACING, Direction.NORTH).setValue(StairBlock.SHAPE, StairsShape.STRAIGHT);
        putIfInside(blocks, -2, 4, -19, seat);
        putIfInside(blocks, 2, 4, -19, seat);

        for (int x = -2; x <= 2; x++) {
            putIfInside(blocks, x, 4, -21, Blocks.SMOOTH_QUARTZ_SLAB.defaultBlockState());
        }
        putIfInside(blocks, 0, 4, -22, Blocks.CRAFTING_TABLE.defaultBlockState());

        putIfInside(blocks, -4, 3, -17, Blocks.SEA_LANTERN.defaultBlockState());
        putIfInside(blocks, 4, 3, -17, Blocks.SEA_LANTERN.defaultBlockState());
    }

    private static void placeLounge(Map<BlockPos, BlockState> blocks) {
        floorRect(blocks, 4, -7, 7, -14, -6, Blocks.SPRUCE_PLANKS.defaultBlockState());

        for (int z = -13; z <= -7; z += 3) {
            putIfInside(blocks, -6, 5, z, Blocks.SPRUCE_STAIRS.defaultBlockState()
                    .setValue(StairBlock.FACING, Direction.EAST).setValue(StairBlock.SHAPE, StairsShape.STRAIGHT));
            putIfInside(blocks, -5, 5, z, Blocks.SPRUCE_SLAB.defaultBlockState());
            putIfInside(blocks, -5, 6, z, Blocks.LANTERN.defaultBlockState());

            putIfInside(blocks, 6, 5, z, Blocks.SPRUCE_STAIRS.defaultBlockState()
                    .setValue(StairBlock.FACING, Direction.WEST).setValue(StairBlock.SHAPE, StairsShape.STRAIGHT));
            putIfInside(blocks, 5, 5, z, Blocks.SPRUCE_SLAB.defaultBlockState());
            putIfInside(blocks, 5, 6, z, Blocks.OCHRE_FROGLIGHT.defaultBlockState());
        }

        putIfInside(blocks, -7, 5, -10, Blocks.BOOKSHELF.defaultBlockState());
        putIfInside(blocks, -7, 5, -9, Blocks.BOOKSHELF.defaultBlockState());
        putIfInside(blocks, 7, 5, -10, Blocks.BARREL.defaultBlockState());
        putIfInside(blocks, 7, 5, -9, Blocks.BARREL.defaultBlockState());

        for (int z = -12; z <= -7; z += 2) {
            putIfInside(blocks, -3, 5, z, Blocks.LIGHT_BLUE_CARPET.defaultBlockState());
            putIfInside(blocks, 3, 5, z, Blocks.WHITE_CARPET.defaultBlockState());
        }
    }

    private static void placeCabins(Map<BlockPos, BlockState> blocks) {
        floorRect(blocks, 4, -7, 7, -5, 3, Blocks.SPRUCE_PLANKS.defaultBlockState());

        placeBed(blocks, -6, 5, -3, Direction.SOUTH);
        putIfInside(blocks, -6, 5, -1, Blocks.BARREL.defaultBlockState());
        putIfInside(blocks, -7, 5, -3, Blocks.OCHRE_FROGLIGHT.defaultBlockState());

        placeBed(blocks, 6, 5, -3, Direction.SOUTH);
        putIfInside(blocks, 6, 5, -1, Blocks.CHEST.defaultBlockState()
                .setValue(ChestBlock.FACING, Direction.WEST).setValue(ChestBlock.TYPE, ChestType.SINGLE));
        putIfInside(blocks, 7, 5, -3, Blocks.OCHRE_FROGLIGHT.defaultBlockState());

        placeBed(blocks, -6, 5, 1, Direction.NORTH);
        placeBed(blocks, 6, 5, 1, Direction.NORTH);

        for (int z = -4; z <= 2; z += 2) {
            putIfInside(blocks, -3, 5, z, Blocks.WHITE_CARPET.defaultBlockState());
            putIfInside(blocks, 3, 5, z, Blocks.WHITE_CARPET.defaultBlockState());
        }
    }

    private static void placeDiningNook(Map<BlockPos, BlockState> blocks) {
        floorRect(blocks, 4, -7, 7, 4, 10, Blocks.SPRUCE_PLANKS.defaultBlockState());

        placeDiningSet(blocks, -4, 7);
        placeDiningSet(blocks, 4, 7);

        putIfInside(blocks, -7, 5, 6, Blocks.BOOKSHELF.defaultBlockState());
        putIfInside(blocks, 7, 5, 6, Blocks.BARREL.defaultBlockState());
    }

    private static void placeDiningSet(Map<BlockPos, BlockState> blocks, int x, int z) {
        putIfInside(blocks, x, 5, z, Blocks.SPRUCE_SLAB.defaultBlockState());
        putIfInside(blocks, x, 6, z, Blocks.LANTERN.defaultBlockState());
        Direction towardTable = x < 0 ? Direction.EAST : Direction.WEST;
        putIfInside(blocks, x - 1, 5, z, Blocks.SPRUCE_STAIRS.defaultBlockState()
                .setValue(StairBlock.FACING, towardTable).setValue(StairBlock.SHAPE, StairsShape.STRAIGHT));
        putIfInside(blocks, x + 1, 5, z, Blocks.SPRUCE_STAIRS.defaultBlockState()
                .setValue(StairBlock.FACING, towardTable.getOpposite()).setValue(StairBlock.SHAPE, StairsShape.STRAIGHT));
    }

    private static void placeEngineRoom(Map<BlockPos, BlockState> blocks) {
        floorRect(blocks, 3, -7, 7, 11, 20, Blocks.SPRUCE_PLANKS.defaultBlockState());

        for (int z = 12; z <= 19; z += 3) {
            putIfInside(blocks, -5, 4, z, Blocks.BLAST_FURNACE.defaultBlockState()
                    .setValue(HorizontalDirectionalBlock.FACING, Direction.EAST));
            putIfInside(blocks, -6, 4, z, Blocks.COPPER_BLOCK.defaultBlockState());
            putIfInside(blocks, -6, 5, z, Blocks.LIGHTNING_ROD.defaultBlockState());

            putIfInside(blocks, 5, 4, z, Blocks.FURNACE.defaultBlockState()
                    .setValue(HorizontalDirectionalBlock.FACING, Direction.WEST));
            putIfInside(blocks, 6, 4, z, Blocks.IRON_BLOCK.defaultBlockState());
            putIfInside(blocks, 6, 5, z, Blocks.SEA_LANTERN.defaultBlockState());
        }

        // Glowing power-core strip down the centre aisle floor.
        for (int z = 12; z <= 19; z += 4) {
            putIfInside(blocks, 0, 3, z, Blocks.GLOWSTONE.defaultBlockState());
        }
    }

    private static void placeRearObservation(Map<BlockPos, BlockState> blocks) {
        floorRect(blocks, 4, -5, 5, 21, 25, Blocks.SMOOTH_QUARTZ.defaultBlockState());

        BlockState bench = Blocks.SPRUCE_STAIRS.defaultBlockState()
                .setValue(StairBlock.FACING, Direction.SOUTH).setValue(StairBlock.SHAPE, StairsShape.STRAIGHT);
        for (int x = -4; x <= 4; x += 2) {
            if (Math.abs(x) <= 1) {
                continue;
            }
            putIfInside(blocks, x, 5, 24, bench);
        }
        putIfInside(blocks, -4, 5, 22, Blocks.LANTERN.defaultBlockState());
        putIfInside(blocks, 4, 5, 22, Blocks.LANTERN.defaultBlockState());
    }

    // --- shared interior helpers -------------------------------------------------

    private static void floorRect(Map<BlockPos, BlockState> blocks, int y, int xMin, int xMax, int zMin, int zMax, BlockState material) {
        for (int x = xMin; x <= xMax; x++) {
            for (int z = zMin; z <= zMax; z++) {
                if (insideHull(x, y, z)) {
                    blocks.put(local(x, y, z), material);
                }
            }
        }
    }

    private static void putIfInside(Map<BlockPos, BlockState> blocks, int x, int y, int z, BlockState state) {
        if (insideHull(x, y, z)) {
            blocks.put(local(x, y, z), state);
        }
    }

    private static void placeBed(Map<BlockPos, BlockState> blocks, int x, int y, int z, Direction facing) {
        BlockPos foot = new BlockPos(x, y, z);
        BlockPos head = foot.relative(facing);
        if (!insideHull(foot.getX(), foot.getY(), foot.getZ()) || !insideHull(head.getX(), head.getY(), head.getZ())) {
            return;
        }
        blocks.put(foot, Blocks.BLUE_BED.defaultBlockState()
                .setValue(BedBlock.FACING, facing).setValue(BedBlock.PART, BedPart.FOOT));
        blocks.put(head, Blocks.BLUE_BED.defaultBlockState()
                .setValue(BedBlock.FACING, facing).setValue(BedBlock.PART, BedPart.HEAD));
    }

    // --- queries used by the rest of the mod --------------------------------

    // The hull layout never changes at runtime, but protectedLocalPositions() is consulted on every
    // block break/place near the ship, so the (expensive) geometry pass is cached after the first call.
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
            return new BlockPos(MIN_X, MIN_Y, BOW_Z);
        }

        @Override
        public BlockPos maxLocal() {
            return new BlockPos(MAX_X, HEIGHT - 1, STERN_Z);
        }

        @Override
        public Map<BlockPos, BlockState> buildBlocks() {
            return OceanPearlSubmarineBuilder.buildBlocks();
        }

        @Override
        public List<SeatSpec> seats() {
            return SEATS;
        }

        @Override
        public SeatSpec seatAt(BlockPos localPos) {
            return OceanPearlSubmarineBuilder.seatAt(localPos);
        }

        @Override
        public boolean isEditable(BlockPos localPos) {
            return OceanPearlSubmarineBuilder.isEditable(localPos);
        }

        @Override
        public Set<Long> protectedLocalPositions() {
            return OceanPearlSubmarineBuilder.protectedLocalPositions();
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
            // Bow is at z = BOW_Z (most negative), stern at z = STERN_Z, so forward is -Z.
            return new Vector3d(0.0, 0.0, -1.0);
        }
    };
}
