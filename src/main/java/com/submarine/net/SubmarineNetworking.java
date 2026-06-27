package com.submarine.net;

import com.submarine.SubmarineMod;
import com.submarine.entity.SubmarineSeatEntity;
import io.netty.buffer.Unpooled;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public final class SubmarineNetworking {
    public static final ResourceLocation PILOT_INPUT = new ResourceLocation(SubmarineMod.MOD_ID, "pilot_input");
    public static final ResourceLocation QUIT_CLIENT = new ResourceLocation(SubmarineMod.MOD_ID, "quit_client");
    private static final Map<Long, SubmarineInput> INPUTS = new ConcurrentHashMap<>();

    private SubmarineNetworking() {
    }

    public static FriendlyByteBuf makeInputPacket(float forward, float turn, float vertical) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeFloat(forward);
        buf.writeFloat(turn);
        buf.writeFloat(vertical);
        return buf;
    }

    public static void registerServerReceivers() {
        ServerPlayNetworking.registerGlobalReceiver(PILOT_INPUT, (server, player, handler, buf, responseSender) -> {
            float forward = buf.readFloat();
            float turn = buf.readFloat();
            float vertical = buf.readFloat();
            server.execute(() -> acceptPilotInput(player, forward, turn, vertical));
        });
    }

    public static SubmarineInput getInput(long shipId) {
        return INPUTS.get(shipId);
    }

    public static void clearInput(long shipId) {
        INPUTS.remove(shipId);
    }

    private static void acceptPilotInput(ServerPlayer player, float forward, float turn, float vertical) {
        if (!(player.getVehicle() instanceof SubmarineSeatEntity seat) || !seat.isPilotSeat()) {
            return;
        }
        long shipId = seat.getShipId();
        UUID pilot = player.getUUID();
        INPUTS.put(shipId, new SubmarineInput(clamp(forward), clamp(turn), clamp(vertical), pilot, player.serverLevel().getGameTime()));
    }

    private static float clamp(float value) {
        if (value > 1.0F) {
            return 1.0F;
        }
        if (value < -1.0F) {
            return -1.0F;
        }
        return value;
    }
}
