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
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.StairsShape;

public final class StarterSubmarineTemplate {
    public static final String ID = "starter_sub";
    public static final int LENGTH = 23;
    public static final int HEIGHT = 7;
    public static final int WIDTH = 7;
    public static final BlockPos CENTER = new BlockPos(11, 2, 3);
    public static final double FORWARD_FORCE = 180000.0;
    public static final double VERTICAL_FORCE = 140000.0;
    public static final double YAW_TORQUE = 950000.0;
    public static final double LINEAR_DRAG = 12000.0;
    public static final double ANGULAR_DRAG = 80000.0;
    public static final int IDLE_STATIC_AFTER_TICKS = 8;
    public static final double BUOYANCY_GRAVITY = 10.0;
    public static final double MIN_DEPTH = -60.0;

    public static final List<SeatSpec> SEATS = List.of(
            new SeatSpec(0, new BlockPos(2, 1, 3), Direction.WEST),
            new SeatSpec(1, new BlockPos(5, 1, 2), Direction.WEST),
            new SeatSpec(2, new BlockPos(5, 1, 4), Direction.WEST),
            new SeatSpec(3, new BlockPos(13, 1, 2), Direction.WEST),
            new SeatSpec(4, new BlockPos(13, 1, 4), Direction.WEST)
    );

    public static final List<BlockBox> EDITABLE_BOXES = List.of(
            new BlockBox(new BlockPos(1, 1, 1), new BlockPos(20, 3, 5)),
            new BlockBox(new BlockPos(8, 4, 2), new BlockPos(12, 5, 4))
    );

    private StarterSubmarineTemplate() {
    }

    public static Map<BlockPos, BlockState> buildBlocks() {
        Map<BlockPos, BlockState> blocks = new HashMap<>();
        BlockState hull = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
        BlockState platedHull = Blocks.DEEPSLATE_TILES.defaultBlockState();
        BlockState glass = Blocks.CYAN_STAINED_GLASS.defaultBlockState();

        for (int x = 0; x < LENGTH; x++) {
            for (int y = 0; y <= 4; y++) {
                for (int z = 0; z < WIDTH; z++) {
                    boolean shell = y == 0 || y == 4 || z == 0 || z == WIDTH - 1;
                    if (shell) {
                        blocks.put(local(x, y, z), (x % 5 == 0 || y == 0) ? platedHull : hull);
                    }
                }
            }
        }

        for (int y = 1; y <= 3; y++) {
            for (int z = 1; z <= 5; z++) {
                blocks.put(local(0, y, z), glass);
            }
        }

        // Back wall — closes the open stern
        for (int y = 1; y <= 3; y++) {
            for (int z = 1; z <= 5; z++) {
                blocks.put(local(LENGTH - 1, y, z), hull);
            }
        }

        for (int x : new int[]{5, 6, 10, 11, 15, 16}) {
            blocks.put(local(x, 2, 0), glass);
            blocks.put(local(x, 2, 6), glass);
        }

        for (int x = 8; x <= 12; x++) {
            for (int z = 2; z <= 4; z++) {
                boolean rim = x == 8 || x == 12 || z == 2 || z == 4;
                blocks.put(local(x, 5, z), rim ? hull : Blocks.AIR.defaultBlockState());
            }
        }
        blocks.put(local(10, 4, 3), Blocks.OAK_TRAPDOOR.defaultBlockState()
                .setValue(TrapDoorBlock.HALF, Half.TOP)
                .setValue(TrapDoorBlock.FACING, Direction.NORTH));

        putSeatBlocks(blocks);
        putInteriorBlocks(blocks);

        blocks.values().removeIf(BlockState::isAir);
        return blocks;
    }

    public static Set<Long> protectedLocalPositions() {
        Set<Long> protectedPositions = new HashSet<>();
        buildBlocks().keySet().forEach(pos -> protectedPositions.add(pos.asLong()));
        return protectedPositions;
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
            blocks.put(seat.localPos(), Blocks.DARK_OAK_STAIRS.defaultBlockState()
                    .setValue(StairBlock.FACING, seat.facing())
                    .setValue(StairBlock.SHAPE, StairsShape.STRAIGHT));
        }
    }

    private static void putInteriorBlocks(Map<BlockPos, BlockState> blocks) {
        blocks.put(local(4, 2, 1), Blocks.SEA_LANTERN.defaultBlockState());
        blocks.put(local(8, 3, 3), Blocks.SEA_LANTERN.defaultBlockState());
        blocks.put(local(14, 2, 5), Blocks.SEA_LANTERN.defaultBlockState());
        blocks.put(local(19, 3, 3), Blocks.SEA_LANTERN.defaultBlockState());

        for (int y = 1; y <= 3; y++) {
            blocks.put(local(10, y, 3), Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH));
        }

        blocks.put(local(7, 1, 1), Blocks.BARREL.defaultBlockState());
        blocks.put(local(7, 1, 5), Blocks.BARREL.defaultBlockState());
        blocks.put(local(17, 1, 1), Blocks.BARREL.defaultBlockState());
        blocks.put(local(17, 1, 5), Blocks.BARREL.defaultBlockState());
        blocks.put(local(9, 1, 1), Blocks.CHEST.defaultBlockState()
                .setValue(ChestBlock.FACING, Direction.SOUTH)
                .setValue(ChestBlock.TYPE, ChestType.SINGLE));
        blocks.put(local(18, 1, 3), Blocks.FURNACE.defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.WEST));
    }

    private static BlockPos local(int x, int y, int z) {
        return new BlockPos(x, y, z);
    }
}
