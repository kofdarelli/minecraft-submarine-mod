package com.submarine.net;

import com.submarine.entity.SubmarineSeatEntity;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;

public final class SubmarineClientInput {
    private SubmarineClientInput() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(SubmarineClientInput::tick);
    }

    private static void tick(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (player == null || !(player.getVehicle() instanceof SubmarineSeatEntity seat) || !seat.isPilotSeat()) {
            return;
        }

        Options options = minecraft.options;
        float forward = axis(options.keyUp.isDown(), options.keyDown.isDown());
        float turn = axis(options.keyLeft.isDown(), options.keyRight.isDown());
        float vertical = axis(options.keyJump.isDown(), options.keyShift.isDown());
        ClientPlayNetworking.send(SubmarineNetworking.PILOT_INPUT,
                SubmarineNetworking.makeInputPacket(forward, turn, vertical));
    }

    private static float axis(boolean positive, boolean negative) {
        if (positive == negative) {
            return 0.0F;
        }
        return positive ? 1.0F : -1.0F;
    }
}
