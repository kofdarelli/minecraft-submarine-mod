package com.submarine.client;

import com.submarine.SubmarineMod;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

public final class SubmarineAutomationClient {
    private static final String WORLD_ENV = "SUBMARINE_AUTOMATION_WORLD";
    private static final String COMMAND_ENV = "SUBMARINE_AUTOMATION_COMMAND";
    private static boolean openRequested;
    private static boolean readyLogged;
    private static boolean commandSent;

    private SubmarineAutomationClient() {
    }

    public static void register() {
        String worldName = System.getenv(WORLD_ENV);
        if (worldName == null || worldName.isBlank()) {
            return;
        }

        String command = System.getenv(COMMAND_ENV);
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick(client, worldName.trim(), command));
        SubmarineMod.LOGGER.info("Submarine automation world requested: {}", worldName.trim());
    }

    private static void tick(Minecraft client, String worldName, String command) {
        if (client.player != null) {
            if (!readyLogged) {
                readyLogged = true;
                SubmarineMod.LOGGER.info("Submarine automation world ready: {}", worldName);
            }
            if (!commandSent && command != null && !command.isBlank() && client.player.connection != null) {
                commandSent = true;
                String normalized = command.trim();
                if (normalized.startsWith("/")) {
                    normalized = normalized.substring(1);
                }
                SubmarineMod.LOGGER.info("Running submarine automation command: /{}", normalized);
                client.player.connection.sendCommand(normalized);
            }
            return;
        }

        if (openRequested || client.level != null || client.screen == null) {
            return;
        }

        openRequested = true;
        if (client.getLevelSource().levelExists(worldName)) {
            SubmarineMod.LOGGER.info("Opening existing submarine automation world: {}", worldName);
            client.createWorldOpenFlows().loadLevel(client.screen, worldName);
            return;
        }

        SubmarineMod.LOGGER.info("Creating submarine automation world: {}", worldName);
        LevelSettings settings = new LevelSettings(
                worldName,
                GameType.CREATIVE,
                false,
                Difficulty.PEACEFUL,
                true,
                new GameRules(),
                WorldDataConfiguration.DEFAULT
        );
        client.createWorldOpenFlows().createFreshLevel(
                worldName,
                settings,
                WorldOptions.defaultWithRandomSeed().withStructures(false),
                WorldPresets::createNormalWorldDimensions
        );
    }
}
