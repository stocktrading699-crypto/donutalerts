package com.dasa.donutalerts;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Alerts {
    private Alerts() {}

    public static Config cfg = Config.load();

    // Matches: "Name paid you $1M", "Name paid you $ 11M", "Name paid you $75K", "$1,000,000"
    private static final Pattern PAY = Pattern.compile(
            "(\\S+)\\s+paid you\\s+\\$\\s*([\\d,]*\\.?\\d+)\\s*([KkMmBbTt]?)\\b");

    public static final int TOAST_W = 180, TOAST_H = 38;
    public static final int AUC_W = 230, AUC_H = 62;

    // ---------------------------------------------------------------- state
    public static final class Toast {
        final String name;
        final double amount;
        final Config.Tier tier;
        final SkinTextures skin;
        final long start = System.currentTimeMillis();

        Toast(String name, double amount, Config.Tier tier, SkinTextures skin) {
            this.name = name;
            this.amount = amount;
            this.tier = tier;
            this.skin = skin;
        }
    }

    private static final List<Toast> toasts = new ArrayList<>();
    private static Toast banner;
    private static final List<Config.Effect> pending = new ArrayList<>();

    // auction
    private static boolean aActive, aEnded;
    private static long aEnd, aEndedUntil;
    private static double aMin, aBid;
    private static int aQty;
    private static String aItem = "", aItemId = "minecraft:elytra", aBidder;
    private static SkinTextures aSkin;

    private static String lastKey = "";
    private static long lastKeyTime = 0;

    // ---------------------------------------------------------------- input
    public static void onMessage(Text message) {
        if (!cfg.enabled) return;
        Matcher m = PAY.matcher(message.getString());
        if (!m.find()) return;
        double amount;
        try {
            amount = Fmt.parseLoose(m.group(2) + m.group(3));
        } catch (Exception e) {
            return;
        }
        String name = m.group(1);

        // ignore the same message arriving twice
        String key = name + "|" + amount;
        long now = System.currentTimeMillis();
        if (key.equals(lastKey) && now - lastKeyTime < 150) return;
        lastKey = key;
        lastKeyTime = now;

        handlePayment(name, amount);
    }

    public static void handlePayment(String name, double amount) {
        if (aActive && amount >= aMin && amount > aBid) {
            aBid = amount;
            aBidder = name;
            aSkin = skinFor(name);
        }
        if (amount < cfg.globalMin) return;
        Config.Tier t = cfg.tierFor(amount);
        if (t == null) return;
        trigger(name, amount, t);
    }

    public static void trigger(String name, double amount, Config.Tier t) {
        Toast toast = new Toast(name, amount, t, skinFor(name));
        if (t.display == Config.Display.CENTER) {
            banner = toast;
        } else {
            toasts.add(0, toast);
            while (toasts.size() > 5) toasts.remove(toasts.size() - 1);
        }
        if (t.effect != Config.Effect.NONE) pending.add(t.effect);
        playSound(t.sound);
    }

    private static SkinTextures skinFor(String name) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getNetworkHandler() == null) return null;
        PlayerListEntry e = mc.getNetworkHandler().getPlayerListEntry(name);
        return e == null ? null : e.getSkinTextures();
    }

    public static void playSound(String friendlyName) {
        String id = Config.SOUNDS.get(friendlyName);
        if (id == null || id.isEmpty()) return;
        Identifier ident = Identifier.tryParse(id);
        if (ident == null) return;
        MinecraftClient.getInstance().getSoundManager()
                .play(PositionedSoundInstance.master(SoundEvent.of(ident), 1.0f, cfg.volume));
    }

    // ---------------------------------------------------------------- auction
    public static void startAuction() {
        aActive = true;
        aEnded = false;
        int secs = Fmt.parseDuration(cfg.auctionTime);
        if (secs <= 0) secs = 30;
        aEnd = System.currentTimeMillis() + secs * 1000L;
        aMin = cfg.auctionMin;
        aQty = cfg.auctionQty;
        aItemId = cfg.auctionItemId;
        aItem = itemName(aItemId);
        aBidder = null;
        aBid = 0;
        aSkin = null;
    }

    private static Item itemOf(String id) {
        Identifier i = Identifier.tryParse(id == null ? "" : id);
        return i == null ? Items.ELYTRA : Registries.ITEM.get(i);
    }

    public static String itemName(String id) {
        return itemOf(id).getName().getString();
    }

    public static ItemStack itemStack(String id) {
        return new ItemStack(itemOf(id));
    }

    public static void stopAuction() {
        aActive = false;
        aEnded = false;
    }

    public static String statusLine() {
        if (aActive) return "Running - highest: " + (aBidder == null ? "no bids yet" : aBidder + " " + Fmt.money(aBid));
        if (aEnded) return "Ended - " + (aBidder == null ? "no winner" : "winner " + aBidder + " " + Fmt.money(aBid));
        return "No auction running";
    }

    // ---------------------------------------------------------------- positions
    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    public static int[] toastPos(int w, int h) {
        return new int[]{
                clamp((int) (cfg.toastX * w), 0, Math.max(0, w - TOAST_W)),
                clamp((int) (cfg.toastY * h), 0, Math.max(0, h - TOAST_H))};
    }

    public static int[] auctionPos(int w, int h) {
        return new int[]{
                clamp((int) (cfg.auctionX * w), 0, Math.max(0, w - AUC_W)),
                clamp((int) (cfg.auctionY * h), 0, Math.max(0, h - AUC_H))};
    }

    // ---------------------------------------------------------------- particles
    private static final class P {
        float x, y, vx, vy, g, drag, age, life, size, delay, fuse;
        int color;
        boolean rocket;
    }

    private static final List<P> parts = new ArrayList<>();
    private static final Random R = new Random();
    private static float flash = 0;
    private static long lastNanos = 0;

    private static final int[] CONFETTI = {0xFF5555, 0x55FF55, 0x5599FF, 0xFFD700, 0xFF66CC, 0x55FFFF};
    private static final int[] FIRE = {0xFFFFAA, 0xFFD700, 0xFF8833, 0xFF4422};

    private static void spawn(Config.Effect e, int w, int h) {
        switch (e) {
            case CONFETTI -> confetti(w, h);
            case ROCKETS -> rockets(w, h);
            case EXPLOSION -> explosion(w, h);
            case EVERYTHING -> {
                confetti(w, h);
                rockets(w, h);
                explosion(w, h);
            }
            default -> {}
        }
    }

    private static void confetti(int w, int h) {
        for (int i = 0; i < 170; i++) {
            P p = new P();
            p.x = R.nextFloat() * w;
            p.y = -R.nextFloat() * h * 0.4f;
            p.vx = (R.nextFloat() - 0.5f) * 50f;
            p.vy = 40f + R.nextFloat() * 90f;
            p.g = 40f;
            p.life = 5f;
            p.size = 2f + R.nextFloat() * 3f;
            p.color = CONFETTI[R.nextInt(CONFETTI.length)];
            parts.add(p);
        }
    }

    private static void rockets(int w, int h) {
        for (int i = 0; i < 6; i++) {
            P p = new P();
            p.rocket = true;
            p.delay = i * 0.25f + R.nextFloat() * 0.2f;
            p.x = w * (0.15f + 0.7f * R.nextFloat());
            p.y = h;
            float burstY = h * (0.2f + 0.3f * R.nextFloat());
            p.fuse = 1.0f + R.nextFloat() * 0.4f;
            p.vy = -(h - burstY) / p.fuse;
            p.vx = (R.nextFloat() - 0.5f) * 20f;
            p.color = 0xFFD700;
            parts.add(p);
        }
    }

    private static void burst(List<P> out, float x, float y) {
        int base = CONFETTI[R.nextInt(CONFETTI.length)];
        for (int i = 0; i < 36; i++) {
            double ang = R.nextDouble() * Math.PI * 2;
            float sp = 40f + R.nextFloat() * 90f;
            P p = new P();
            p.x = x;
            p.y = y;
            p.vx = (float) Math.cos(ang) * sp;
            p.vy = (float) Math.sin(ang) * sp;
            p.g = 60f;
            p.drag = 0.5f;
            p.life = 1.0f + R.nextFloat() * 0.6f;
            p.size = 2f;
            p.color = R.nextInt(3) == 0 ? 0xFFFFFF : base;
            out.add(p);
        }
    }

    private static void explosion(int w, int h) {
        flash = 0.35f;
        float cx = w / 2f, cy = h * 0.32f;
        for (int i = 0; i < 150; i++) {
            double ang = R.nextDouble() * Math.PI * 2;
            float sp = 30f + (float) Math.pow(R.nextFloat(), 0.6) * 280f;
            P p = new P();
            p.x = cx;
            p.y = cy;
            p.vx = (float) Math.cos(ang) * sp;
            p.vy = (float) Math.sin(ang) * sp;
            p.g = 80f;
            p.drag = 1.2f;
            p.life = 0.9f + R.nextFloat() * 0.8f;
            p.size = 2f + R.nextFloat() * 3f;
            p.color = FIRE[R.nextInt(FIRE.length)];
            parts.add(p);
        }
    }

    private static void update(float dt) {
        List<P> add = new ArrayList<>();
        for (Iterator<P> it = parts.iterator(); it.hasNext(); ) {
            P p = it.next();
            if (p.delay > 0) {
                p.delay -= dt;
                continue;
            }
            p.age += dt;
            if (p.rocket) {
                p.x += p.vx * dt;
                p.y += p.vy * dt;
                P tr = new P();
                tr.x = p.x;
                tr.y = p.y;
                tr.vy = 20f;
                tr.life = 0.35f;
                tr.size = 1.5f;
                tr.color = 0xFF8833;
                add.add(tr);
                if (p.age >= p.fuse) {
                    burst(add, p.x, p.y);
                    it.remove();
                }
            } else {
                float damp = Math.max(0f, 1f - p.drag * dt);
                p.vx *= damp;
                p.vy = p.vy * damp + p.g * dt;
                p.x += p.vx * dt;
                p.y += p.vy * dt;
                if (p.age >= p.life) it.remove();
            }
        }
        parts.addAll(add);
        if (parts.size() > 1500) parts.subList(0, parts.size() - 1500).clear();
        if (flash > 0) flash -= dt;
    }

    private static void drawParticles(DrawContext ctx, int w, int h) {
        if (flash > 0) {
            int a = (int) (flash / 0.35f * 90);
            if (a >= 4) ctx.fill(0, 0, w, h, (a << 24) | 0xFFFFFF);
        }
        for (P p : parts) {
            if (p.delay > 0) continue;
            if (p.rocket) {
                ctx.fill((int) p.x, (int) p.y, (int) p.x + 2, (int) p.y + 5, 0xFFFFE9A0);
                continue;
            }
            float remain = p.life - p.age;
            int a = (int) (255 * Math.max(0f, Math.min(1f, remain / 0.4f)));
            if (a < 4) continue;
            int s = Math.max(1, Math.round(p.size));
            ctx.fill((int) p.x, (int) p.y, (int) p.x + s, (int) p.y + s, (a << 24) | p.color);
        }
    }

    // ---------------------------------------------------------------- drawing helpers
    private static TextRenderer tr() {
        return MinecraftClient.getInstance().textRenderer;
    }

    public static void drawHead(DrawContext ctx, SkinTextures skin, String name, int x, int y, int size) {
        if (skin != null) {
            PlayerSkinDrawer.draw(ctx, skin, x, y, size);
        } else {
            ctx.fill(x, y, x + size, y + size, 0xFF3A3A4A);
            String s = (name == null || name.isEmpty()) ? "?" : name.substring(0, 1).toUpperCase();
            ctx.drawText(tr(), s, x + size / 2 - 3, y + size / 2 - 4, 0xFFFFFFFF, true);
        }
    }

    private static void drawScaledCentered(DrawContext ctx, String text, float cx, float y, float scale, int argb) {
        TextRenderer t = tr();
        MatrixStack m = ctx.getMatrices();
        m.push();
        m.translate(cx, y, 0f);
        m.scale(scale, scale, 1f);
        ctx.drawText(t, text, -t.getWidth(text) / 2, 0, argb, true);
        m.pop();
    }

    public static void drawToast(DrawContext ctx, int x, int y, String name, double amount, int color, SkinTextures skin) {
        TextRenderer t = tr();
        ctx.fill(x, y, x + TOAST_W, y + TOAST_H, 0xD00E1420);
        ctx.drawBorder(x, y, TOAST_W, TOAST_H, 0xFF000000 | color);
        drawHead(ctx, skin, name, x + 5, y + 5, 28);
        ctx.drawText(t, name + " paid you", x + 40, y + 7, 0xFFB8B8C8, false);
        MatrixStack m = ctx.getMatrices();
        m.push();
        m.translate(x + 40, y + 19, 0f);
        m.scale(1.6f, 1.6f, 1f);
        ctx.drawText(t, Fmt.money(amount), 0, 0, 0xFF000000 | color, true);
        m.pop();
    }

    private static String fmtTime(long ms) {
        long s = (long) Math.ceil(ms / 1000.0);
        return String.format("%d:%02d", s / 60, s % 60);
    }

    public static void drawAuctionBox(DrawContext ctx, int x, int y, boolean active, long msLeft, double min,
                                      int qty, String item, String itemId, String bidder, double bid, SkinTextures skin) {
        TextRenderer t = tr();
        ctx.fill(x, y, x + AUC_W, y + AUC_H, 0xD00E1420);
        ctx.drawBorder(x, y, AUC_W, AUC_H, 0xFFFFAA00);
        String title = active ? "Bidding - " + fmtTime(msLeft) + " left" : "Bidding Ended!";
        ctx.drawText(t, title, x + 8, y + 6, 0xFFFFAA00, true);
        if (active) {
            String m = "Min " + Fmt.money(min);
            ctx.drawText(t, m, x + AUC_W - 8 - t.getWidth(m), y + 6, 0xFFAAAAAA, false);
        }
        drawHead(ctx, skin, bidder == null ? "?" : bidder, x + 8, y + 22, 32);
        if (bidder == null) {
            ctx.drawText(t, active ? "No bids yet" : "No winner", x + 46, y + 34, 0xFFAAAAAA, false);
        } else if (active) {
            ctx.drawText(t, bidder, x + 46, y + 26, 0xFF5599FF, false);
            ctx.drawText(t, Fmt.money(bid), x + 46, y + 38, 0xFF55FF55, true);
        } else {
            ctx.drawText(t, "Winner:", x + 46, y + 24, 0xFFAAAAAA, false);
            ctx.drawText(t, bidder + " (" + Fmt.money(bid) + ")", x + 46, y + 36, 0xFF5599FF, false);
        }
        String it = qty + "x " + item;
        int iw = t.getWidth(it);
        ctx.drawText(t, it, x + AUC_W - 8 - iw, y + 22, 0xFFFFFFFF, true);
        ctx.drawItem(itemStack(itemId), x + AUC_W - 24, y + 38);
    }

    // ---------------------------------------------------------------- main render
    public static void renderAll(DrawContext ctx) {
        int w = ctx.getScaledWindowWidth(), h = ctx.getScaledWindowHeight();
        long nn = System.nanoTime();
        float dt = lastNanos == 0 ? 0f : Math.min(0.05f, (nn - lastNanos) / 1e9f);
        lastNanos = nn;

        for (Config.Effect e : pending) spawn(e, w, h);
        pending.clear();
        update(dt);
        drawParticles(ctx, w, h);
        renderBanner(ctx, w, h);
        renderToasts(ctx, w, h);
        renderAuction(ctx, w, h);
    }

    private static void renderBanner(DrawContext ctx, int w, int h) {
        if (banner == null) return;
        float t = (System.currentTimeMillis() - banner.start) / 1000f;
        float total = 4.5f;
        if (t > total) {
            banner = null;
            return;
        }
        float fade = Math.min(1f, (total - t) / 0.7f);
        float pop = t < 0.25f ? 0.5f + 0.5f * (t / 0.25f) : 1f;
        int a = Math.max(4, (int) (255 * fade));
        float cx = w / 2f, y = h * 0.2f;

        drawHead(ctx, banner.skin, banner.name, (int) cx - 16, (int) y, 32);
        drawScaledCentered(ctx, banner.name + " paid you", cx, y + 38, 1.6f * pop, (a << 24) | 0xFFFFFF);
        drawScaledCentered(ctx, Fmt.money(banner.amount), cx, y + 54, 4.5f * pop, (a << 24) | banner.tier.color);
    }

    private static void renderToasts(DrawContext ctx, int w, int h) {
        long now = System.currentTimeMillis();
        toasts.removeIf(t -> now - t.start > 5000);
        int[] pos = toastPos(w, h);
        int i = 0;
        for (Toast t : toasts) {
            drawToast(ctx, pos[0], pos[1] + i * (TOAST_H + 4), t.name, t.amount, t.tier.color, t.skin);
            i++;
        }
    }

    private static void renderAuction(DrawContext ctx, int w, int h) {
        if (!aActive && !aEnded) return;
        long now = System.currentTimeMillis();
        if (aActive && now >= aEnd) {
            aActive = false;
            aEnded = true;
            aEndedUntil = now + 15000;
            if (aBidder != null) playSound("Level Up");
        }
        if (aEnded && now > aEndedUntil) {
            aEnded = false;
            return;
        }
        int[] pos = auctionPos(w, h);
        drawAuctionBox(ctx, pos[0], pos[1], aActive, Math.max(0, aEnd - now), aMin, aQty, aItem, aItemId, aBidder, aBid, aSkin);
    }
}
