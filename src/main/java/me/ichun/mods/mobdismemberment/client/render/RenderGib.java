package me.ichun.mods.mobdismemberment.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Vector3f;
import me.ichun.mods.mobdismemberment.client.entity.EntityGib;
import me.ichun.mods.mobdismemberment.common.core.Config;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public class RenderGib extends EntityRenderer<EntityGib> {
    private static final ResourceLocation FALLBACK_TEXTURE = new ResourceLocation("textures/entity/zombie/zombie.png");

    public RenderGib(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(EntityGib gib) {
        if (gib.texture != null) {
            return gib.texture;
        }
        return FALLBACK_TEXTURE;
    }

    @Override
    public void render(EntityGib gib, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        if (gib.modelPart == null) {
            return;
        }

        poseStack.pushPose();

        // Calculate alpha for fade out
        float alpha = Mth.clamp(
                gib.groundTime >= Config.GIB_GROUND_TIME.get()
                        ? 1.0F - (gib.groundTime - Config.GIB_GROUND_TIME.get() + partialTicks) / 20F
                        : 1.0F,
                0F, 1F
        );

        // Apply rotation
        float yaw = gib.getInterpolatedYaw(partialTicks);
        float pitch = gib.getInterpolatedPitch(partialTicks);

        poseStack.mulPose(Vector3f.YP.rotationDegrees(-yaw));
        poseStack.mulPose(Vector3f.XP.rotationDegrees(pitch));

        // Flip model (standard for entity rendering)
        poseStack.scale(-1.0F, -1.0F, 1.0F);

        // Translate to center the geometry at origin (center values are in model units)
        // This makes the rotation happen around the geometric center
        poseStack.translate(-gib.centerX / 16.0F, -gib.centerY / 16.0F, -gib.centerZ / 16.0F);

        // Get texture and render
        ResourceLocation texture = getTextureLocation(gib);
        RenderType renderType = RenderType.entityTranslucent(texture);
        VertexConsumer vertexConsumer = buffer.getBuffer(renderType);

        // Save original pivot and rotation values
        float origX = gib.modelPart.x;
        float origY = gib.modelPart.y;
        float origZ = gib.modelPart.z;
        float origXRot = gib.modelPart.xRot;
        float origYRot = gib.modelPart.yRot;
        float origZRot = gib.modelPart.zRot;

        // Zero out pivot and rotation so part renders at its cube positions only
        gib.modelPart.x = 0;
        gib.modelPart.y = 0;
        gib.modelPart.z = 0;
        gib.modelPart.xRot = 0;
        gib.modelPart.yRot = 0;
        gib.modelPart.zRot = 0;

        // Render the stored model part
        gib.modelPart.render(poseStack, vertexConsumer, packedLight, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, alpha);

        // Restore original values
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
