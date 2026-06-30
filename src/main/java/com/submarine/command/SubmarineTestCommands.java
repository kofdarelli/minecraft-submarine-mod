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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
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
    private static final Path RESULT_DIR = Path.of("build", "test-results", "submarine-smoke");
    private static final Path RESULT_JSON = RESULT_DIR.resolve("results.json");
    private static final List<TestSpec> TESTS = List.of(
            new TestSpec("template", "template integrity", SubmarineTestCommands::testTemplateIntegrity),
            new TestSpec("metadata", "metadata round trip", SubmarineTestCommands::testMetadataRoundTrip),
            new TestSpec("spawn", "starter submarine spawn", SubmarineTestCommands::testStarterSubmarineSpawn)
    );

    private SubmarineTestCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("submarine")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("test")
                        .then(Commands.literal("run")
                                .executes(context -> runAll(context.getSource())))
                        .then(Commands.literal("template")
                                .executes(context -> runSingle(context.getSource(), "template")))
                        .then(Commands.literal("metadata")
                                .executes(context -> runSingle(context.getSource(), "metadata")))
                        .then(Commands.literal("spawn")
                                .executes(context -> runSingle(context.getSource(), "spawn")))
                        .then(Commands.literal("quit-client")
                                .executes(context -> quitClient(context.getSource())))));
    }

    private static int runAll(CommandSourceStack source) {
        return runSuite(source, TESTS, "submarine integration tests");
    }

    private static int runSingle(CommandSourceStack source, String id) {
        for (TestSpec test : TESTS) {
            if (test.id().equals(id)) {
                return runSuite(source, List.of(test), "submarine " + test.name() + " test");
            }
        }
        source.sendFailure(Component.literal("Unknown submarine test: " + id));
        return 0;
    }

    private static int runSuite(CommandSourceStack source, List<TestSpec> tests, String suiteName) {
        TestContext context = TestContext.create(source);
        if (context == null) {
            return 0;
        }

        TestSummary summary = new TestSummary(suiteName);
        source.sendSuccess(() -> Component.literal("Running " + suiteName + "..."), false);
        SubmarineMod.LOGGER.info("Running {}", suiteName);

        for (TestSpec test : tests) {
            runTest(context, summary, test);
        }

        writeResultArtifact(summary);
        return reportSummary(source, summary);
    }

    private static int reportSummary(CommandSourceStack source, TestSummary summary) {
        if (summary.allPassed()) {
            String resultMessage = String.format("All submarine tests passed (%d/%d)", summary.passed(), summary.ran());
            source.sendSuccess(() -> Component.literal(resultMessage), false);
            SubmarineMod.LOGGER.info(resultMessage);
            return Command.SINGLE_SUCCESS;
        }

        String resultMessage = String.format("Some submarine tests failed (%d/%d passed)", summary.passed(), summary.ran());
        source.sendFailure(Component.literal(resultMessage));
        SubmarineMod.LOGGER.warn(resultMessage);
        return 0;
    }

    private static void runTest(TestContext context, TestSummary summary, TestSpec test) {
        context.source().sendSuccess(() -> Component.literal(" - " + test.name() + "..."), false);
        long started = System.nanoTime();
        try {
            test.body().run(context);
            long durationMillis = durationMillis(started);
            summary.add(TestResult.pass(test.id(), test.name(), durationMillis));
            context.source().sendSuccess(() -> Component.literal("   PASS " + test.name()), false);
        } catch (Exception exception) {
            long durationMillis = durationMillis(started);
            String message = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
            summary.add(TestResult.fail(test.id(), test.name(), durationMillis, message));
            context.source().sendFailure(Component.literal("   FAIL " + test.name() + ": " + message));
            SubmarineMod.LOGGER.warn("Submarine integration test failed: {}", test.name(), exception);
        }
    }

    private static void testTemplateIntegrity(TestContext context) {
        Map<BlockPos, BlockState> blocks = StarterSubmarineTemplate.buildBlocks();
        require(!blocks.isEmpty(), "starter template has no blocks");
        require(blocks.get(new BlockPos(0, 4, 4)).is(Blocks.CYAN_STAINED_GLASS), "cockpit canopy is missing");
        require(blocks.get(new BlockPos(15, 5, 4)).is(Blocks.LADDER), "conning tower ladder is missing");
        require(blocks.get(new BlockPos(15, 11, 4)).is(Blocks.SEA_LANTERN), "conning tower beacon is missing");
        require(StarterSubmarineTemplate.SEATS.size() == 5, "expected 5 seat specs");
        require(StarterSubmarineTemplate.seatAt(new BlockPos(3, 3, 4)) != null, "pilot seat spec is missing");
        require(StarterSubmarineTemplate.isEditable(new BlockPos(10, 4, 4)), "interior edit volume is not editable");
        require(!StarterSubmarineTemplate.isEditable(new BlockPos(0, 0, 0)), "outer hull should not be editable");
        require(StarterSubmarineTemplate.protectedLocalPositions().contains(new BlockPos(3, 3, 4).asLong()),
                "pilot seat is not protected");
        context.source().sendSuccess(() -> Component.literal("   checked " + blocks.size() + " template blocks"), false);
    }

    private static void testMetadataRoundTrip(TestContext context) {
        UUID owner = UUID.nameUUIDFromBytes("submarine-test-owner".getBytes(StandardCharsets.UTF_8));
        BlockPos origin = new BlockPos(10, 20, 30);
        SubmarineMetadata metadata = new SubmarineMetadata(42L, owner, StarterSubmarineTemplate.ID, "minecraft:overworld", origin);
        SubmarineMetadata loaded = SubmarineMetadata.load(metadata.save());

        require(loaded.shipId() == metadata.shipId(), "ship id changed");
        require(loaded.owner().equals(metadata.owner()), "owner changed");
        require(loaded.templateId().equals(metadata.templateId()), "template id changed");
        require(loaded.dimensionId().equals(metadata.dimensionId()), "dimension id changed");
        require(loaded.shipyardOrigin().equals(metadata.shipyardOrigin()), "shipyard origin changed");
        require(loaded.toLocal(origin.offset(3, 4, 5)).equals(new BlockPos(3, 4, 5)), "local conversion failed");
        context.source().sendSuccess(() -> Component.literal("   metadata save/load is stable"), false);
    }

    private static void testStarterSubmarineSpawn(TestContext context) {
        BlockPos origin = testOrigin(context.player());
        clearSpawnArea(context.level(), origin);

        ServerShip ship = SubmarineSpawner.spawnStarterSub(context.level(), context.player(), origin);
        require(ship != null, "ship assembler returned null");

        SubmarineMetadata metadata = SubmarineSavedData.get(context.level()).get(ship.getId())
                .orElseThrow(() -> new IllegalStateException("spawned ship metadata was not saved"));
        require(StarterSubmarineTemplate.ID.equals(metadata.templateId()), "spawned metadata uses the wrong template");
        require(context.player().getUUID().equals(metadata.owner()), "spawned metadata uses the wrong owner");

        SubmarineSeatManager.ensureSeats(context.level(), metadata);
        List<SubmarineSeatEntity> seats = findSeats(context.level(), metadata.shipId(), metadata.shipyardOrigin());
        require(seats.size() == StarterSubmarineTemplate.SEATS.size(),
                "expected " + StarterSubmarineTemplate.SEATS.size() + " seats, found " + seats.size());
        require(seats.stream().anyMatch(SubmarineSeatEntity::isPilotSeat), "pilot seat entity was not created");

        context.source().sendSuccess(() -> Component.literal("   spawned ship id " + ship.getId() + " with " + seats.size() + " seats"), false);
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

    private static void writeResultArtifact(TestSummary summary) {
        try {
            Files.createDirectories(RESULT_DIR);
            Files.writeString(RESULT_JSON, summary.toJson(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            SubmarineMod.LOGGER.warn("Could not write submarine test result artifact", exception);
        }
    }

    private static long durationMillis(long startedNanos) {
        return Math.max(0L, (System.nanoTime() - startedNanos) / 1_000_000L);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private static String escapeJson(String value) {
        StringBuilder escaped = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\':
                    escaped.append("\\\\");
                    break;
                case '"':
                    escaped.append("\\\"");
                    break;
                case '\n':
                    escaped.append("\\n");
                    break;
                case '\r':
                    escaped.append("\\r");
                    break;
                case '\t':
                    escaped.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) c));
                    } else {
                        escaped.append(c);
                    }
            }
        }
        return escaped.toString();
    }

    @FunctionalInterface
    private interface TestBody {
        void run(TestContext context);
    }

    private record TestSpec(String id, String name, TestBody body) {
    }

    private record TestContext(CommandSourceStack source, ServerPlayer player, ServerLevel level) {
        private static TestContext create(CommandSourceStack source) {
            ServerPlayer player;
            try {
                player = source.getPlayerOrException();
            } catch (Exception exception) {
                source.sendFailure(Component.literal("Submarine tests must be run by a player."));
                return null;
            }
            return new TestContext(source, player, player.serverLevel());
        }
    }

    private record TestResult(String id, String name, String status, long durationMillis, String message) {
        private static TestResult pass(String id, String name, long durationMillis) {
            return new TestResult(id, name, "PASS", durationMillis, "");
        }

        private static TestResult fail(String id, String name, long durationMillis, String message) {
            return new TestResult(id, name, "FAIL", durationMillis, message);
        }

        private String toJson() {
            return "    {"
                    + "\"id\":\"" + escapeJson(id) + "\","
                    + "\"name\":\"" + escapeJson(name) + "\","
                    + "\"status\":\"" + status + "\","
                    + "\"durationMillis\":" + durationMillis + ","
                    + "\"message\":\"" + escapeJson(message) + "\""
                    + "}";
        }
    }

    private static final class TestSummary {
        private final String suiteName;
        private final String startedAt = Instant.now().toString();
        private final List<TestResult> results = new ArrayList<>();

        private TestSummary(String suiteName) {
            this.suiteName = suiteName;
        }

        private void add(TestResult result) {
            results.add(result);
        }

        private int ran() {
            return results.size();
        }

        private int passed() {
            int passed = 0;
            for (TestResult result : results) {
                if ("PASS".equals(result.status())) {
                    passed++;
                }
            }
            return passed;
        }

        private boolean allPassed() {
            return ran() > 0 && passed() == ran();
        }

        private String toJson() {
            StringBuilder json = new StringBuilder();
            json.append("{\n");
            json.append("  \"suite\":\"").append(escapeJson(suiteName)).append("\",\n");
            json.append("  \"startedAt\":\"").append(startedAt).append("\",\n");
            json.append("  \"status\":\"").append(allPassed() ? "PASS" : "FAIL").append("\",\n");
            json.append("  \"passed\":").append(passed()).append(",\n");
            json.append("  \"total\":").append(ran()).append(",\n");
            json.append("  \"tests\":[\n");
            for (int i = 0; i < results.size(); i++) {
                json.append(results.get(i).toJson());
                if (i < results.size() - 1) {
                    json.append(',');
                }
                json.append('\n');
            }
            json.append("  ]\n");
            json.append("}\n");
            return json.toString();
        }
    }
}
