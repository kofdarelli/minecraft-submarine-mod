package com.submarine.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.submarine.data.SubmarineMetadata;
import com.submarine.data.SubmarineSavedData;
import com.submarine.entity.ModEntities;
import com.submarine.entity.SubmarineSeatEntity;
import com.submarine.template.SubmarineTemplate;
import com.submarine.template.SubmarineTemplates;
import java.util.ArrayList;
import java.util.Optional;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import org.valkyrienskies.core.api.ships.ServerShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

public final class SubmarineManageCommands {
    private static final SimpleCommandExceptionType NOT_FOUND =
            new SimpleCommandExceptionType(Component.literal("No submarine with that ID found."));

    private SubmarineManageCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("submarine")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("list")
                        .executes(ctx -> listAll(ctx.getSource())))
                .then(Commands.literal("delete")
                        .then(Commands.argument("id", LongArgumentType.longArg())
                                .executes(ctx -> delete(
                                        ctx.getSource(),
                                        LongArgumentType.getLong(ctx, "id")))))
                .then(Commands.literal("transfer")
                        .then(Commands.argument("id", LongArgumentType.longArg())
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> transfer(
                                                ctx.getSource(),
                                                LongArgumentType.getLong(ctx, "id"),
                                                EntityArgument.getPlayer(ctx, "player")))))));
    }

    private static int listAll(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        int count = 0;
        for (ServerLevel level : server.getAllLevels()) {
            for (SubmarineMetadata meta : SubmarineSavedData.get(level).all()) {
                ServerShip ship = VSGameUtilsKt.getShipObjectWorld(level).getAllShips().getById(meta.shipId());
                String posStr = ship != null
                        ? String.format("%.0f / %.0f / %.0f",
                                ship.getTransform().getPositionInWorld().x(),
                                ship.getTransform().getPositionInWorld().y(),
                                ship.getTransform().getPositionInWorld().z())
                        : "unloaded";
                String owner = resolveOwnerName(server, meta);
                String dim = meta.dimensionId().replace("minecraft:", "");
                long id = meta.shipId();
                source.sendSuccess(() -> Component.literal(
                        "  ID " + id + " | owner: " + owner + " | " + posStr + " | " + dim), false);
                count++;
            }
        }
        if (count == 0) {
            source.sendSuccess(() -> Component.literal("No submarines registered."), false);
        } else {
            int total = count;
            source.sendSuccess(() -> Component.literal(total + " submarine(s) total."), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int delete(CommandSourceStack source, long shipId) throws CommandSyntaxException {
        LevelAndMeta found = findAcrossLevels(source.getServer(), shipId);
        if (found == null) {
            throw NOT_FOUND.create();
        }

        ServerLevel level = found.level();
        SubmarineMetadata meta = found.meta();

        ejectAllPassengers(level, meta);

        SubmarineTemplate template = SubmarineTemplates.get(meta.templateId());
        for (BlockPos localPos : template.allLocalPositions()) {
            level.setBlock(meta.toShipyard(localPos), Blocks.AIR.defaultBlockState(), 3);
        }

        SubmarineSavedData.get(level).remove(shipId);
        source.sendSuccess(() -> Component.literal("Submarine " + shipId + " deleted."), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int transfer(CommandSourceStack source, long shipId, ServerPlayer newOwner)
            throws CommandSyntaxException {
        LevelAndMeta found = findAcrossLevels(source.getServer(), shipId);
        if (found == null) {
            throw NOT_FOUND.create();
        }

        SubmarineMetadata updated = found.meta().withOwner(newOwner.getUUID());
        SubmarineSavedData.get(found.level()).put(updated);
        source.sendSuccess(() -> Component.literal(
                "Submarine " + shipId + " transferred to " + newOwner.getName().getString() + "."), true);
        return Command.SINGLE_SUCCESS;
    }

    private static LevelAndMeta findAcrossLevels(MinecraftServer server, long shipId) {
        for (ServerLevel level : server.getAllLevels()) {
            Optional<SubmarineMetadata> meta = SubmarineSavedData.get(level).get(shipId);
            if (meta.isPresent()) {
                return new LevelAndMeta(level, meta.get());
            }
        }
        return null;
    }

    private static void ejectAllPassengers(ServerLevel level, SubmarineMetadata meta) {
        BlockPos origin = meta.shipyardOrigin();
        SubmarineTemplate template = SubmarineTemplates.get(meta.templateId());
        AABB searchBox = new AABB(origin).inflate(template.searchRadius());
        for (SubmarineSeatEntity seat : new ArrayList<>(
                level.getEntities(ModEntities.SEAT, searchBox, s -> s.getShipId() == meta.shipId()))) {
            seat.ejectPassengers();
            seat.discard();
        }
    }

    private static String resolveOwnerName(MinecraftServer server, SubmarineMetadata meta) {
        ServerPlayer online = server.getPlayerList().getPlayer(meta.owner());
        if (online != null) {
            return online.getName().getString();
        }
        return server.getProfileCache()
                .get(meta.owner())
                .map(com.mojang.authlib.GameProfile::getName)
                .orElse(meta.owner().toString().substring(0, 8) + "...");
    }

    private record LevelAndMeta(ServerLevel level, SubmarineMetadata meta) {
    }
}
