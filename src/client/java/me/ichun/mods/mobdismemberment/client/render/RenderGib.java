package me.ichun.mods.mobdismemberment.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import me.ichun.mods.mobdismemberment.client.entity.EntityGib;
import me.ichun.mods.mobdismemberment.common.core.Config;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

public class RenderGib extends EntityRenderer<EntityGib, GibRenderState> {
    private static final Identifier FALLBACK_TEXTURE = Identifier.fromNamespaceAndPath("minecraft", "textures/entity/zombie/zombie.png");

    public RenderGib(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public GibRenderState createRenderState() {
        return new GibRenderState();
    }

    @Override
    public void extractRenderState(EntityGib gib, GibRenderState state, float partialTicks) {
        super.extractRenderState(gib, state, partialTicks);
        state.gib = gib;
        state.partialTicks = partialTicks;
    }

    @Override
    public boolean affectedByCulling(EntityGib entity) {
        return false;
    }

    @Override
    public void submit(GibRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState cameraRenderState) {
        EntityGib gib = state.gib;
        if (gib == null || gib.modelPart == null) {
            super.submit(state, poseStack, collector, cameraRenderState);
            return;
        }

        float partialTicks = state.partialTicks;

        poseStack.pushPose();

        float alpha = Mth.clamp(
                gib.groundTime >= Config.getGibGroundTime()
                        ? 1.0F - (gib.groundTime - Config.getGibGroundTime() + partialTicks) / 20F
                        : 1.0F,
                0F, 1F
        );

        float yaw = gib.getInterpolatedYaw(partialTicks);
        float pitch = gib.getInterpolatedPitch(partialTicks);

        poseStack.mulPose(Axis.YP.rotationDegrees(-yaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(pitch));

        poseStack.scale(-1.0F, -1.0F, 1.0F);

        poseStack.translate(-gib.centerX / 16.0F, -gib.centerY / 16.0F, -gib.centerZ / 16.0F);

        Identifier texture = gib.texture != null ? gib.texture : FALLBACK_TEXTURE;
        RenderType renderType = RenderTypes.entityTranslucent(texture);

        float origX = gib.modelPart.x;
        float origY = gib.modelPart.y;
        float origZ = gib.modelPart.z;
        float origXRot = gib.modelPart.xRot;
        float origYRot = gib.modelPart.yRot;
        float origZRot = gib.modelPart.zRot;

        int color = ARGB.colorFromFloat(alpha, 1.0F, 1.0F, 1.0F);

        collector.order(0).submitCustomGeometry(poseStack, renderType, (pose, vertexConsumer) -> {
            gib.modelPart.x = 0;
            gib.modelPart.y = 0;
            gib.modelPart.z = 0;
            gib.modelPart.xRot = 0;
            gib.modelPart.yRot = 0;
            gib.modelPart.zRot = 0;

            PoseStack modelPose = new PoseStack();
            modelPose.last().pose().set(pose.pose());
            modelPose.last().normal().set(pose.normal());
            gib.modelPart.render(modelPose, vertexConsumer, state.lightCoords, OverlayTexture.NO_OVERLAY, color);

            gib.modelPart.x = origX;
            gib.modelPart.y = origY;
            gib.modelPart.z = origZ;
            gib.modelPart.xRot = origXRot;
            gib.modelPart.yRot = origYRot;
            gib.modelPart.zRot = origZRot;
        });

        poseStack.popPose();

        super.submit(state, poseStack, collector, cameraRenderState);
    }
}
