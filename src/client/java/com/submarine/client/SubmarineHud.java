package com.submarine.client;

import com.submarine.entity.SubmarineSeatEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

public final class SubmarineHud {
    private SubmarineHud() {
    }

    public static void render(GuiGraphics graphics, float tickDelta) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.screen != null) {
            return;
        }
        if (!(client.player.getVehicle() instanceof SubmarineSeatEntity seat)) {
            return;
        }

        int seaLevel = client.level != null ? client.level.getSeaLevel() : 63;
        int depth = Math.max(0, (int) Math.round(seaLevel - client.player.getY()));

        if (depth > 0) {
            int alpha = Math.min(80, depth * 3);
            int w = client.getWindow().getGuiScaledWidth();
            int h = client.getWindow().getGuiScaledHeight();
            graphics.fill(0, 0, w, h, (alpha << 24) | 0x000820);
        }

        String roleText = seat.isPilotSeat() ? "PILOT" : "PASSENGER";
        int roleColor = seat.isPilotSeat() ? 0x00FF88 : 0xFFAA00;
        String depthText = depth > 0 ? "Depth: " + depth + "m" : "Surface";

        graphics.drawString(client.font, roleText, 5, 5, roleColor);
        graphics.drawString(client.font, depthText, 5, 17, 0xFFFFFF);
        graphics.drawString(client.font, "Sneak to exit", 5, 29, 0xAAAAAA);
    }
}
