package me.ichun.mods.mobdismemberment.client.render;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.vertex.IVertexBuilder;
import me.ichun.mods.mobdismemberment.client.entity.EntityGib;
import me.ichun.mods.mobdismemberment.common.core.Config;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererManager;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Vector3f;

public class RenderGib extends EntityRenderer<EntityGib> {
    private static final ResourceLocation FALLBACK_TEXTURE = new ResourceLocation("textures/entity/zombie/zombie.png");

    public RenderGib(EntityRendererManager manager) {
        super(manager);
    }

    @Override
    public ResourceLocation getTextureLocation(EntityGib gib) {
        if (gib.texture != null) {
            return gib.texture;
        }
        return FALLBACK_TEXTURE;
    }

    @Override
    public void render(EntityGib gib, float entityYaw, float partialTicks, MatrixStack poseStack, IRenderTypeBuffer buffer, int packedLight) {
        if (gib.modelPart == null) {
            return;
        }

        poseStack.pushPose();

        float alpha = MathHelper.clamp(
                gib.groundTime >= Config.GIB_GROUND_TIME.get()
                        ? 1.0F - (gib.groundTime - Config.GIB_GROUND_TIME.get() + partialTicks) / 20F
                        : 1.0F,
                0F, 1F
        );

        float yaw = gib.getInterpolatedYaw(partialTicks);
        float pitch = gib.getInterpolatedPitch(partialTicks);

        poseStack.mulPose(Vector3f.YP.rotationDegrees(-yaw));
        poseStack.mulPose(Vector3f.XP.rotationDegrees(pitch));

        poseStack.scale(-1.0F, -1.0F, 1.0F);

        poseStack.translate(-gib.centerX / 16.0F, -gib.centerY / 16.0F, -gib.centerZ / 16.0F);

        ResourceLocation texture = getTextureLocation(gib);
        RenderType renderType = RenderType.entityTranslucent(texture);
        IVertexBuilder vertexConsumer = buffer.getBuffer(renderType);

        float origX = gib.modelPart.x;
        float origY = gib.modelPart.y;
        float origZ = gib.modelPart.z;
        float origXRot = gib.modelPart.xRot;
        float origYRot = gib.modelPart.yRot;
        float origZRot = gib.modelPart.zRot;

        gib.modelPart.x = 0;
        gib.modelPart.y = 0;
        gib.modelPart.z = 0;
        gib.modelPart.xRot = 0;
        gib.modelPart.yRot = 0;
        gib.modelPart.zRot = 0;

        gib.modelPart.render(poseStack, vertexConsumer, packedLight, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, alpha);

        gib.modelPart.x = origX;
        gib.modelPart.y = origY;
        gib.modelPart.z = origZ;
        gib.modelPart.xRot = origXRot;
        gib.modelPart.yRot = origYRot;
        gib.modelPart.zRot = origZRot;

        poseStack.popPose();

        super.render(gib, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }
}
