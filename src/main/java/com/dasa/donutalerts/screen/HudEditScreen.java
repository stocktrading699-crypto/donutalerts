package com.dasa.donutalerts.screen;

import com.dasa.donutalerts.Alerts;
import com.dasa.donutalerts.Config;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.text.Text;

public class HudEditScreen extends BaseScreen {
    private int drag = -1; // 0 = toast, 1 = auction
    private int offX, offY;

    public HudEditScreen() {
        super(Text.literal("Move boxes"));
    }

    private SkinTextures mySkin() {
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc.player == null ? null : mc.player.getSkinTextures();
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, 0x55000000);
        int[] t = Alerts.toastPos(width, height);
        int[] a = Alerts.auctionPos(width, height);
        SkinTextures skin = mySkin();
        Alerts.drawToast(ctx, t[0], t[1], "SampleUser", 5_000_000, 0x55FF55, skin);
        ctx.drawText(textRenderer, "Payment alerts", t[0], t[1] - 10, 0xFFFFFFFF, true);
        Alerts.drawAuctionBox(ctx, a[0], a[1], true, 30_000, 10_000_000, 1, "Elytra", "minecraft:elytra", "SampleUser", 12_000_000, skin);
        ctx.drawText(textRenderer, "Auction box", a[0], a[1] - 10, 0xFFFFFFFF, true);
        ctx.drawCenteredTextWithShadow(textRenderer,
                Text.literal("Drag the boxes where you want them. Press Esc when done."), width / 2, 10, 0xFFFFFFFF);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            int[] t = Alerts.toastPos(width, height);
            int[] a = Alerts.auctionPos(width, height);
            if (mx >= a[0] && mx <= a[0] + Alerts.AUC_W && my >= a[1] && my <= a[1] + Alerts.AUC_H) {
                drag = 1;
                offX = (int) mx - a[0];
                offY = (int) my - a[1];
                return true;
            }
            if (mx >= t[0] && mx <= t[0] + Alerts.TOAST_W && my >= t[1] && my <= t[1] + Alerts.TOAST_H) {
                drag = 0;
                offX = (int) mx - t[0];
                offY = (int) my - t[1];
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (drag >= 0) {
            Config c = Alerts.cfg;
            double fx = Math.max(0, Math.min(1, (mx - offX) / (double) width));
            double fy = Math.max(0, Math.min(1, (my - offY) / (double) height));
            if (drag == 0) {
                c.toastX = fx;
                c.toastY = fy;
            } else {
                c.auctionX = fx;
                c.auctionY = fy;
            }
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        drag = -1;
        Alerts.cfg.save();
        return super.mouseReleased(mx, my, button);
    }
}
