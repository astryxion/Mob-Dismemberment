package me.ichun.mods.mobdismemberment.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import me.ichun.mods.mobdismemberment.client.entity.EntityGib;
import me.ichun.mods.mobdismemberment.client.render.state.GibRenderState;
import me.ichun.mods.mobdismemberment.common.core.Config;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

public class RenderGib extends EntityRenderer<EntityGib, GibRenderState> {
    private static final Identifier FALLBACK_TEXTURE = Identifier.withDefaultNamespace("textures/entity/zombie/zombie.png");

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
        state.modelPart = gib.modelPart;
        state.texture = gib.texture != null ? gib.texture : FALLBACK_TEXTURE;
        state.centerX = gib.centerX;
        state.centerY = gib.centerY;
        state.centerZ = gib.centerZ;
        state.alpha = Mth.clamp(
                gib.groundTime >= Config.GIB_GROUND_TIME.get()
                        ? 1.0F - (gib.groundTime - Config.GIB_GROUND_TIME.get() + partialTicks) / 20F
                        : 1.0F,
                0F, 1F
        );
        state.interpolatedYaw = gib.getInterpolatedYaw(partialTicks);
        state.interpolatedPitch = gib.getInterpolatedPitch(partialTicks);
    }

    @Override
    protected boolean affectedByCulling(EntityGib display) {
        return false;
    }

    @Override
    public void submit(GibRenderState state, PoseStack poseStack, SubmitNodeCollector nodeCollector, CameraRenderState cameraRenderState) {
        if (state.modelPart == null) {
            return;
        }

        poseStack.pushPose();

        float yaw = state.interpolatedYaw;
        float pitch = state.interpolatedPitch;

        poseStack.mulPose(Axis.YP.rotationDegrees(-yaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(pitch));

        poseStack.scale(-1.0F, -1.0F, 1.0F);

        poseStack.translate(-state.centerX / 16.0F, -state.centerY / 16.0F, -state.centerZ / 16.0F);

        Identifier texture = state.texture;
        RenderType renderType = RenderTypes.entityTranslucent(texture, false);
        int rgba = ARGB.color(Mth.floor(state.alpha * 255.0F), 255, 255, 255);

        net.minecraft.client.model.geom.ModelPart part = state.modelPart;
        float origX = part.x;
        float origY = part.y;
        float origZ = part.z;
        float origXRot = part.xRot;
        float origYRot = part.yRot;
        float origZRot = part.zRot;

        part.x = 0;
        part.y = 0;
        part.z = 0;
        part.xRot = 0;
        part.yRot = 0;
        part.zRot = 0;

        nodeCollector.submitModelPart(part, poseStack, renderType, state.lightCoords, OverlayTexture.NO_OVERLAY, null, rgba, null);

        part.x = origX;
        part.y = origY;
        part.z = origZ;
        part.xRot = origXRot;
        part.yRot = origYRot;
        part.zRot = origZRot;

        poseStack.popPose();
    }
}
