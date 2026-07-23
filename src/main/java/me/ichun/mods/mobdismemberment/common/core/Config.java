package me.ichun.mods.mobdismemberment.common.core;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import me.ichun.mods.mobdismemberment.common.MobDismemberment;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.config.ConfigTracker;
import net.minecraftforge.fml.config.ModConfig;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class Config {
    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.IntValue GIB_TIME;
    public static final ForgeConfigSpec.IntValue GIB_GROUND_TIME;
    public static final ForgeConfigSpec.BooleanValue BLOOD;
    public static final ForgeConfigSpec.IntValue BLOOD_COUNT;
    public static final ForgeConfigSpec.BooleanValue GREEN_BLOOD;
    public static final ForgeConfigSpec.BooleanValue GIB_PUSHING;
    public static final ForgeConfigSpec.ConfigValue<String> MOB_BLACKLIST;

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

        MOB_BLACKLIST = BUILDER
                .comment("Comma-separated entity IDs that should never be dismembered. Example: minecraft:zombie,mutantmonsters:mutant_zombie. Leave empty for none.")
                .define("mobBlacklist", "");

        BUILDER.pop();
        SPEC = BUILDER.build();
    }

    /**
     * Parsed blacklist IDs from {@link #MOB_BLACKLIST} (lowercase, trimmed, empty entries dropped).
     * Surrounding quotes on entries are stripped so UI/file edits like {@code "minecraft:zombie"} still match.
     */
    public static List<String> getMobBlacklist() {
        String raw = MOB_BLACKLIST.get();
        List<String> result = new ArrayList<>();
        if (raw == null || raw.trim().isEmpty() || "none".equalsIgnoreCase(raw.trim()) || "[]".equals(raw.trim())) {
            return result;
        }
        String[] parts = raw.split(",");
        for (int i = 0; i < parts.length; i++) {
            String id = parts[i].trim().toLowerCase(Locale.ROOT);
            if (id.length() >= 2
                    && ((id.startsWith("\"") && id.endsWith("\"")) || (id.startsWith("'") && id.endsWith("'")))) {
                id = id.substring(1, id.length() - 1).trim();
            }
            if (!id.isEmpty()) {
                result.add(id);
            }
        }
        return result;
    }

    /**
     * Re-read {@code mobdismemberment-client.toml} from disk into the live spec.
     * Call on world join so leave-world → edit config → rejoin picks up blacklist changes.
     */
    @SuppressWarnings("unchecked")
    public static void reloadClientConfigFromDisk() {
        try {
            Field configSetsField = ConfigTracker.class.getDeclaredField("configSets");
            configSetsField.setAccessible(true);
            EnumMap<ModConfig.Type, Set<ModConfig>> configSets =
                    (EnumMap<ModConfig.Type, Set<ModConfig>>) configSetsField.get(ConfigTracker.INSTANCE);
            if (configSets == null) {
                return;
            }
            Set<ModConfig> configs = configSets.get(ModConfig.Type.CLIENT);
            if (configs == null) {
                return;
            }
            for (ModConfig config : configs) {
                if (!MobDismemberment.MOD_ID.equals(config.getModId()) || config.getSpec() != SPEC) {
                    continue;
                }
                CommentedConfig data = config.getConfigData();
                if (!(data instanceof CommentedFileConfig)) {
                    continue;
                }
                CommentedFileConfig fileConfig = (CommentedFileConfig) data;
                fileConfig.load();
                SPEC.setConfig(fileConfig);
                SPEC.afterReload();
                // ModConfig.Reloading's ctor is package-private; fire via reflection when available.
                try {
                    java.lang.reflect.Constructor<ModConfig.Reloading> ctor =
                            ModConfig.Reloading.class.getDeclaredConstructor(ModConfig.class);
                    ctor.setAccessible(true);
                    ModConfig.Reloading reloading = ctor.newInstance(config);
                    ModList.get().getModContainerById(config.getModId()).ifPresent(container ->
                            container.dispatchConfigEvent(reloading));
                } catch (ReflectiveOperationException ignored) {
                }
            }
        } catch (ReflectiveOperationException ignored) {
        }
    }
}
