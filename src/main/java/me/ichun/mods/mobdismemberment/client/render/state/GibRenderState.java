package me.ichun.mods.mobdismemberment.client.render.state;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.resources.Identifier;

public class GibRenderState extends EntityRenderState {
    public ModelPart modelPart;
    public Identifier texture;
    public float centerX;
    public float centerY;
    public float centerZ;
    public float alpha;
    public float interpolatedYaw;
    public float interpolatedPitch;
}
