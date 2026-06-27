package com.submarine.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.submarine.SubmarineMod;
import com.submarine.data.SubmarineMetadata;
import com.submarine.data.SubmarineSavedData;
import com.submarine.entity.ModEntities;
import com.submarine.entity.SubmarineSeatEntity;
import com.submarine.net.SubmarineNetworking;
import com.submarine.seat.SubmarineSeatManager;
import com.submarine.template.StarterSubmarineTemplate;
import com.submarine.template.SubmarineSpawner;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.valkyrienskies.core.api.ships.ServerShip;

public final class SubmarineTestCommands {
    private SubmarineTestCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("submarine")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("test")
                        .then(Commands.literal("run")
                                .executes(context -> runAll(context.getSource())))
                        .then(Commands.literal("quit-client")
                                .executes(context -> quitClient(context.getSource())))));
    }

    private static int runAll(CommandSourceStack source) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception exception) {
            source.sendFailure(Component.literal("Submarine tests must be run by a player."));
            return 0;
        }

        ServerLevel level = player.serverLevel();
        TestSummary summary = new TestSummary();
        source.sendSuccess(() -> Component.literal("Running submarine integration tests..."), false);
        SubmarineMod.LOGGER.info("Running submarine integration tests");

        runTest(source, summary, "template integrity", () -> testTemplateIntegrity(source));
        runTest(source, summary, "metadata round trip", () -> testMetadataRoundTrip(source));
        runTest(source, summary, "starter submarine spawn", () -> testStarterSubmarineSpawn(source, player, level));

        String resultMessage;
        if (summary.passed == summary.ran) {
            resultMessage = String.format("All submarine tests passed (%d/%d)", summary.passed, summary.ran);
            source.sendSuccess(() -> Component.literal(resultMessage), false);
            SubmarineMod.LOGGER.info(resultMessage);
            return Command.SINGLE_SUCCESS;
        }

        resultMessage = String.format("Some submarine tests failed (%d/%d passed)", summary.passed, summary.ran);
        source.sendFailure(Component.literal(resultMessage));
        SubmarineMod.LOGGER.warn(resultMessage);
        return 0;
    }

    private static void runTest(CommandSourceStack source, TestSummary summary, String name, TestBody body) {
        summary.ran++;
        source.sendSuccess(() -> Component.literal(" - " + name + "..."), false);
        try {
            body.run();
            summary.passed++;
            source.sendSuccess(() -> Component.literal("   PASS " + name), false);
        } catch (Exception exception) {
            source.sendFailure(Component.literal("   FAIL " + name + ": " + exception.getMessage()));
            SubmarineMod.LOGGER.warn("Submarine integration test failed: {}", name, exception);
        }
    }

    private static void testTemplateIntegrity(CommandSourceStack source) {
        Map<BlockPos, BlockState> blocks = StarterSubmarineTemplate.buildBlocks();
        require(!blocks.isEmpty(), "starter template has no blocks");
        require(blocks.get(new BlockPos(0, 2, 3)).is(Blocks.CYAN_STAINED_GLASS), "front window is missing");
        require(blocks.get(new BlockPos(10, 1, 3)).is(Blocks.LADDER), "interior ladder is missing");
        require(blocks.get(new BlockPos(10, 6, 3)).is(Blocks.SEA_LANTERN), "conning tower light is missing");
        require(StarterSubmarineTemplate.SEATS.size() == 5, "expected 5 seat specs");
        require(StarterSubmarineTemplate.seatAt(new BlockPos(2, 1, 3)) != null, "pilot seat spec is missing");
        require(StarterSubmarineTemplate.isEditable(new BlockPos(2, 2, 2)), "interior edit volume is not editable");
        require(!StarterSubmarineTemplate.isEditable(new BlockPos(0, 0, 0)), "outer hull should not be editable");
        require(StarterSubmarineTemplate.protectedLocalPositions().contains(new BlockPos(0, 2, 3).asLong()),
                "front window is not protected");
        source.sendSuccess(() -> Component.literal("   checked " + blocks.size() + " template blocks"), false);
    }

    private static void testMetadataRoundTrip(CommandSourceStack source) {
        UUID owner = UUID.nameUUIDFromBytes("submarine-test-owner".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        BlockPos origin = new BlockPos(10, 20, 30);
        SubmarineMetadata metadata = new SubmarineMetadata(42L, owner, StarterSubmarineTemplate.ID, "minecraft:overworld", origin);
        SubmarineMetadata loaded = SubmarineMetadata.load(metadata.save());

        require(loaded.shipId() == metadata.shipId(), "ship id changed");
        require(loaded.owner().equals(metadata.owner()), "owner changed");
        require(loaded.templateId().equals(metadata.templateId()), "template id changed");
        require(loaded.dimensionId().equals(metadata.dimensionId()), "dimension id changed");
        require(loaded.shipyardOrigin().equals(metadata.shipyardOrigin()), "shipyard origin changed");
        require(loaded.toLocal(origin.offset(3, 4, 5)).equals(new BlockPos(3, 4, 5)), "local conversion failed");
        source.sendSuccess(() -> Component.literal("   metadata save/load is stable"), false);
    }

    private static void testStarterSubmarineSpawn(CommandSourceStack source, ServerPlayer player, ServerLevel level) {
        BlockPos origin = testOrigin(player);
        clearSpawnArea(level, origin);

        ServerShip ship = SubmarineSpawner.spawnStarterSub(level, player, origin);
        require(ship != null, "ship assembler returned null");

        SubmarineMetadata metadata = SubmarineSavedData.get(level).get(ship.getId())
                .orElseThrow(() -> new IllegalStateException("spawned ship metadata was not saved"));
        require(StarterSubmarineTemplate.ID.equals(metadata.templateId()), "spawned metadata uses the wrong template");
        require(player.getUUID().equals(metadata.owner()), "spawned metadata uses the wrong owner");

        SubmarineSeatManager.ensureSeats(level, metadata);
        List<SubmarineSeatEntity> seats = findSeats(level, metadata.shipId(), metadata.shipyardOrigin());
        require(seats.size() == StarterSubmarineTemplate.SEATS.size(),
                "expected " + StarterSubmarineTemplate.SEATS.size() + " seats, found " + seats.size());
        require(seats.stream().anyMatch(SubmarineSeatEntity::isPilotSeat), "pilot seat entity was not created");

        source.sendSuccess(() -> Component.literal("   spawned ship id " + ship.getId() + " with " + seats.size() + " seats"), false);
    }

    private static BlockPos testOrigin(ServerPlayer player) {
        return player.blockPosition()
                .relative(player.getDirection(), StarterSubmarineTemplate.LENGTH + 10)
                .offset(0, 4, 0);
    }

    private static void clearSpawnArea(ServerLevel level, BlockPos origin) {
        for (int x = -1; x <= StarterSubmarineTemplate.LENGTH + 1; x++) {
            for (int y = -1; y <= StarterSubmarineTemplate.HEIGHT + 1; y++) {
                for (int z = -1; z <= StarterSubmarineTemplate.WIDTH + 1; z++) {
                    level.setBlock(origin.offset(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
    }

    private static List<SubmarineSeatEntity> findSeats(ServerLevel level, long shipId, BlockPos shipyardOrigin) {
        AABB searchBox = new AABB(shipyardOrigin).inflate(StarterSubmarineTemplate.LENGTH + StarterSubmarineTemplate.WIDTH);
        List<SubmarineSeatEntity> seats = new ArrayList<>();
        for (SubmarineSeatEntity seat : level.getEntities(ModEntities.SEAT, searchBox, seat -> seat.getShipId() == shipId)) {
            seats.add(seat);
        }
        return seats;
    }

    private static int quitClient(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.getServer().stopServer();
            return Command.SINGLE_SUCCESS;
        }

        ServerPlayNetworking.send(player, SubmarineNetworking.QUIT_CLIENT, new FriendlyByteBuf(Unpooled.buffer()));
        source.sendSuccess(() -> Component.literal("Requested client shutdown."), false);
        return Command.SINGLE_SUCCESS;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    @FunctionalInterface
    private interface TestBody {
        void run();
    }

    private static final class TestSummary {
        private int ran;
        private int passed;
    }
}
