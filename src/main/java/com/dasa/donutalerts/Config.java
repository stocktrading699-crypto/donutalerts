package com.dasa.donutalerts;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;

public class Config {
    public enum Display { CORNER, CENTER }
    public enum Effect { NONE, CONFETTI, ROCKETS, EXPLOSION, EVERYTHING }

    public static class Tier {
        public double min = 1_000_000;
        public Display display = Display.CORNER;
        public Effect effect = Effect.NONE;
        public String sound = "Pling";
        public int color = 0x55FF55;

        public Tier() {}

        public Tier(double min, Display display, Effect effect, String sound, int color) {
            this.min = min;
            this.display = display;
            this.effect = effect;
            this.sound = sound;
            this.color = color;
        }
    }

    /** Friendly sound name -> Minecraft sound id */
    public static final LinkedHashMap<String, String> SOUNDS = new LinkedHashMap<>();
    static {
        SOUNDS.put("None", "");
        SOUNDS.put("Pling", "minecraft:block.note_block.pling");
        SOUNDS.put("XP Orb", "minecraft:entity.experience_orb.pickup");
        SOUNDS.put("Level Up", "minecraft:entity.player.levelup");
        SOUNDS.put("Bell", "minecraft:block.bell.use");
        SOUNDS.put("Firework", "minecraft:entity.firework_rocket.blast");
        SOUNDS.put("Twinkle", "minecraft:entity.firework_rocket.twinkle");
        SOUNDS.put("Explosion", "minecraft:entity.generic.explode");
        SOUNDS.put("Totem", "minecraft:item.totem.use");
        SOUNDS.put("Challenge", "minecraft:ui.toast.challenge_complete");
    }

    public static final String[] COLOR_NAMES = {"Green", "Aqua", "Blue", "Purple", "Pink", "Red", "Orange", "Gold", "White"};
    public static final int[] COLORS = {0x55FF55, 0x55FFFF, 0x5599FF, 0xAA66FF, 0xFF66CC, 0xFF5555, 0xFFAA33, 0xFFD700, 0xFFFFFF};

    public boolean enabled = true;
    public double globalMin = 1_000_000;
    public float volume = 1.0f;

    // Box positions as a fraction of the screen (top-left corner of the box)
    public double toastX = 0.78, toastY = 0.14;
    public double auctionX = 0.32, auctionY = 0.55;

    public List<Tier> tiers = new ArrayList<>();

    public String auctionItemId = "minecraft:elytra";
    public int auctionQty = 1;
    public String auctionTime = "30s";
    public double auctionMin = 10_000_000;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("donutalerts.json");
    }

    public static Config load() {
        try {
            Path p = path();
            if (Files.exists(p)) {
                Config c = GSON.fromJson(Files.readString(p), Config.class);
                if (c != null && c.tiers != null && !c.tiers.isEmpty()) return c;
            }
        } catch (Exception ignored) {}
        Config c = defaults();
        c.save();
        return c;
    }

    public void save() {
        try {
            Files.writeString(path(), GSON.toJson(this));
        } catch (Exception ignored) {}
    }

    public static Config defaults() {
        Config c = new Config();
        c.tiers.add(new Tier(1_000_000, Display.CORNER, Effect.NONE, "Pling", 0x55FF55));
        c.tiers.add(new Tier(5_000_000, Display.CENTER, Effect.CONFETTI, "XP Orb", 0x55FFFF));
        c.tiers.add(new Tier(10_000_000, Display.CENTER, Effect.EXPLOSION, "Explosion", 0xFFAA33));
        c.tiers.add(new Tier(50_000_000, Display.CENTER, Effect.ROCKETS, "Firework", 0xAA66FF));
        c.tiers.add(new Tier(100_000_000, Display.CENTER, Effect.EVERYTHING, "Totem", 0xFFD700));
        return c;
    }

    public void sortTiers() {
        tiers.sort(Comparator.comparingDouble(t -> t.min));
    }

    /** The tier with the highest minimum that is still <= amount. */
    public Tier tierFor(double amount) {
        Tier best = null;
        for (Tier t : tiers) {
            if (amount >= t.min && (best == null || t.min > best.min)) best = t;
        }
        return best;
    }
}
