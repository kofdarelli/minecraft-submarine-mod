package com.submarine.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.submarine.entity.SubmarineSeatEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

public final class EmptySeatRenderer extends EntityRenderer<SubmarineSeatEntity> {
    private static final ResourceLocation TEXTURE = new ResourceLocation("submarine", "textures/entity/empty.png");

    public EmptySeatRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(SubmarineSeatEntity entity, float yaw, float tickDelta, PoseStack matrices,
                       MultiBufferSource vertexConsumers, int light) {
    }

    @Override
    public ResourceLocation getTextureLocation(SubmarineSeatEntity entity) {
        return TEXTURE;
    }
}
