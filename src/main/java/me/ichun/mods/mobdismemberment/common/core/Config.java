package me.ichun.mods.mobdismemberment.common.core;

import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {
    public static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.IntValue GIB_TIME;
    public static final ModConfigSpec.IntValue GIB_GROUND_TIME;
    public static final ModConfigSpec.BooleanValue BLOOD;
    public static final ModConfigSpec.IntValue BLOOD_COUNT;
    public static final ModConfigSpec.BooleanValue GREEN_BLOOD;
    public static final ModConfigSpec.BooleanValue GIB_PUSHING;

    static {
        BUILDER.comment("Client-side configuration for Mob Dismemberment");
        BUILDER.push("client");

        GIB_TIME = BUILDER
                .comment("How long gibs last (in ticks). Default: 1000")
                .defineInRange("gibTime", 1000, 0, Integer.MAX_VALUE);

        GIB_GROUND_TIME = BUILDER
                .comment("How long gibs last on the ground before fading (in ticks). Default: 100")
                .defineInRange("gibGroundTime", 100, 0, Integer.MAX_VALUE);

        BLOOD = BUILDER
                .comment("Enable blood particles. Default: true")
                .define("blood", true);

        BLOOD_COUNT = BUILDER
                .comment("Number of blood particles to spawn. Default: 100")
                .defineInRange("bloodCount", 100, 1, 1000);

        GREEN_BLOOD = BUILDER
                .comment("Use green blood instead of red. Default: false")
                .define("greenBlood", false);

        GIB_PUSHING = BUILDER
                .comment("Allow gibs to push entities. Default: true")
                .define("gibPushing", true);

        BUILDER.pop();
        SPEC = BUILDER.build();
    }
}
