package com.submarine.command;

import com.mojang.brigadier.Command;
import com.submarine.control.SubmarineController;
import com.submarine.template.SubmarineSpawner;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

public final class SubmarineCommands {
    private SubmarineCommands() {
    }

    public static void register() {
        SubmarineController.register();
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(Commands.literal("submarine")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("spawn")
                                .then(Commands.literal("starter_sub")
                                        .executes(context -> {
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            BlockPos origin = BlockPos.containing(context.getSource().getPosition())
                                                    .relative(player.getDirection(), 4);
                                            SubmarineSpawner.spawnStarterSub(context.getSource().getLevel(), player, origin);
                                            return Command.SINGLE_SUCCESS;
                                        })))));
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                SubmarineTestCommands.register(dispatcher));
    }
}
