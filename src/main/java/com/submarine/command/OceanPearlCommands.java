package com.submarine.command;

import com.mojang.brigadier.Command;
import com.submarine.template.SubmarineSpawner;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

public final class OceanPearlCommands {
    private OceanPearlCommands() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(Commands.literal("oceanpearl")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("build")
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    // Offset well clear of the player: the hull alone is 56 blocks long.
                                    BlockPos origin = player.blockPosition()
                                            .relative(player.getDirection(), 40);
                                    SubmarineSpawner.spawnOceanPearl(context.getSource().getLevel(), player, origin);
                                    return Command.SINGLE_SUCCESS;
                                }))));
    }
}
