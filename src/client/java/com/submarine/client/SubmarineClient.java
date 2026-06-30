package com.submarine.client;

import com.submarine.entity.ModEntities;
import com.submarine.net.SubmarineNetworking;
import com.submarine.net.SubmarineClientInput;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

public final class SubmarineClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(ModEntities.SEAT, EmptySeatRenderer::new);
        SubmarineClientInput.register();
        SubmarineAutomationClient.register();
        HudRenderCallback.EVENT.register(SubmarineHud::render);
        ClientPlayNetworking.registerGlobalReceiver(SubmarineNetworking.QUIT_CLIENT,
                (client, handler, buf, responseSender) -> client.execute(client::stop));
    }
}
