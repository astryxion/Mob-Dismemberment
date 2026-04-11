package me.ichun.mods.mobdismemberment.common.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.ichun.mods.mobdismemberment.common.MobDismemberment;
import net.fabricmc.loader.api.FabricLoader;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Client-side configuration persisted as JSON under the game config directory.
 * Defaults and validation mirror the former Forge {@code ForgeConfigSpec} definitions.
 */
public final class Config {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance()
            .getConfigDir()
            .resolve(MobDismemberment.MOD_ID + "-client.json");

    private static volatile int gibTime = 1000;
    private static volatile int gibGroundTime = 100;
    private static volatile boolean blood = true;
    private static volatile int bloodCount = 100;
    private static volatile boolean greenBlood = false;
    private static volatile boolean gibPushing = true;

    private Config() {
    }

    public static void load() {
        if (!Files.isRegularFile(CONFIG_PATH)) {
            save();
            return;
        }
        try (BufferedReader reader = Files.newBufferedReader(CONFIG_PATH, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            if (root.has("gibTime")) {
                gibTime = clampInt(root.get("gibTime").getAsInt(), 0, Integer.MAX_VALUE, 1000);
            }
            if (root.has("gibGroundTime")) {
                gibGroundTime = clampInt(root.get("gibGroundTime").getAsInt(), 0, Integer.MAX_VALUE, 100);
            }
            if (root.has("blood")) {
                blood = root.get("blood").getAsBoolean();
            }
            if (root.has("bloodCount")) {
                bloodCount = clampInt(root.get("bloodCount").getAsInt(), 1, 1000, 100);
            }
            if (root.has("greenBlood")) {
                greenBlood = root.get("greenBlood").getAsBoolean();
            }
            if (root.has("gibPushing")) {
                gibPushing = root.get("gibPushing").getAsBoolean();
            }
        } catch (Exception ignored) {
            gibTime = 1000;
            gibGroundTime = 100;
            blood = true;
            bloodCount = 100;
            greenBlood = false;
            gibPushing = true;
        }
    }

    public static void save() {
        JsonObject root = new JsonObject();
        root.addProperty("_comment", "Client-side configuration for Mob Dismemberment");
        root.addProperty("gibTime", gibTime);
        root.addProperty("gibGroundTime", gibGroundTime);
        root.addProperty("blood", blood);
        root.addProperty("bloodCount", bloodCount);
        root.addProperty("greenBlood", greenBlood);
        root.addProperty("gibPushing", gibPushing);

        try {
            Files.createDirectories(CONFIG_PATH.getParent());
        } catch (IOException ignored) {
        }
        try (BufferedWriter writer = Files.newBufferedWriter(CONFIG_PATH, StandardCharsets.UTF_8)) {
            GSON.toJson(root, writer);
        } catch (IOException ignored) {
        }
    }

    private static int clampInt(int value, int min, int max, int fallback) {
        if (value < min || value > max) {
            return fallback;
        }
        return value;
    }

    public static int getGibTime() {
        return gibTime;
    }

    public static int getGibGroundTime() {
        return gibGroundTime;
    }

    public static boolean getBlood() {
        return blood;
    }

    public static int getBloodCount() {
        return bloodCount;
    }

    public static boolean getGreenBlood() {
        return greenBlood;
    }

    public static boolean getGibPushing() {
        return gibPushing;
    }
}
