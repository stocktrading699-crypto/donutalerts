package com.dasa.donutalerts.screen;

import com.dasa.donutalerts.Alerts;
import com.dasa.donutalerts.Config;
import com.dasa.donutalerts.Fmt;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

public class TierScreen extends BaseScreen {
    public TierScreen() {
        super(Text.literal("Notification tiers"));
    }

    private static Text colorLabel(int color) {
        String name = Config.COLOR_NAMES[0];
        for (int i = 0; i < Config.COLORS.length; i++) {
            if (Config.COLORS[i] == color) name = Config.COLOR_NAMES[i];
        }
        return Text.literal(name).styled(s -> s.withColor(color));
    }

    private static int nextColor(int color) {
        for (int i = 0; i < Config.COLORS.length; i++) {
            if (Config.COLORS[i] == color) return Config.COLORS[(i + 1) % Config.COLORS.length];
        }
        return Config.COLORS[0];
    }

    private static String nextSound(String current) {
        List<String> keys = new ArrayList<>(Config.SOUNDS.keySet());
        int i = keys.indexOf(current);
        return keys.get((i + 1) % keys.size());
    }

    @Override
    protected void init() {
        Config c = Alerts.cfg;
        c.sortTiers();
        int x0 = width / 2 - 200;
        int y = 44;

        for (Config.Tier t : new ArrayList<>(c.tiers)) {
            addRow(t, x0, y);
            y += 22;
        }

        int by = height - 28;
        if (c.tiers.size() < 8) {
            addDrawableChild(ButtonWidget.builder(Text.literal("+ Add tier"), b -> {
                double last = c.tiers.isEmpty() ? 500_000 : c.tiers.get(c.tiers.size() - 1).min;
                c.tiers.add(new Config.Tier(last * 2, Config.Display.CENTER, Config.Effect.CONFETTI, "XP Orb", 0x55FFFF));
                clearAndInit();
            }).dimensions(width / 2 - 105, by, 100, 20).build());
        }
        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> client.setScreen(new MainScreen()))
                .dimensions(width / 2 + 5, by, 100, 20).build());
    }

    private void addRow(Config.Tier t, int x0, int y) {
        Config c = Alerts.cfg;

        TextFieldWidget f = new TextFieldWidget(textRenderer, x0, y, 60, 18, Text.literal("min"));
        f.setMaxLength(12);
        f.setText(Fmt.shortNum(t.min));
        f.setChangedListener(s -> {
            Double v = Fmt.parseLoose(s);
            if (v != null && v > 0) t.min = v;
        });
        addDrawableChild(f);

        addDrawableChild(ButtonWidget.builder(Text.literal(Fmt.cap(t.display.name())), b -> {
            t.display = t.display == Config.Display.CORNER ? Config.Display.CENTER : Config.Display.CORNER;
            b.setMessage(Text.literal(Fmt.cap(t.display.name())));
        }).dimensions(x0 + 64, y - 1, 54, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal(Fmt.cap(t.effect.name())), b -> {
            Config.Effect[] all = Config.Effect.values();
            t.effect = all[(t.effect.ordinal() + 1) % all.length];
            b.setMessage(Text.literal(Fmt.cap(t.effect.name())));
        }).dimensions(x0 + 122, y - 1, 70, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal(t.sound), b -> {
            t.sound = nextSound(t.sound);
            b.setMessage(Text.literal(t.sound));
            Alerts.playSound(t.sound);
        }).dimensions(x0 + 196, y - 1, 80, 20).build());

        addDrawableChild(ButtonWidget.builder(colorLabel(t.color), b -> {
            t.color = nextColor(t.color);
            b.setMessage(colorLabel(t.color));
        }).dimensions(x0 + 280, y - 1, 56, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Test"),
                b -> Alerts.trigger("TestUser", t.min, t)).dimensions(x0 + 340, y - 1, 32, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("X"), b -> {
            c.tiers.remove(t);
            clearAndInit();
        }).dimensions(x0 + 376, y - 1, 22, 20).build());
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        int x0 = width / 2 - 200;
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("Notification tiers"), width / 2, 8, 0xFFFFD700);
        ctx.drawCenteredTextWithShadow(textRenderer,
                Text.literal("A tier covers payments from its minimum up to the next tier's minimum."), width / 2, 20, 0xFFAAAAAA);
        int hy = 34;
        ctx.drawText(textRenderer, "Min", x0, hy, 0xFFFFFFFF, true);
        ctx.drawText(textRenderer, "Where", x0 + 64, hy, 0xFFFFFFFF, true);
        ctx.drawText(textRenderer, "Effect", x0 + 122, hy, 0xFFFFFFFF, true);
        ctx.drawText(textRenderer, "Sound", x0 + 196, hy, 0xFFFFFFFF, true);
        ctx.drawText(textRenderer, "Color", x0 + 280, hy, 0xFFFFFFFF, true);
    }
}
