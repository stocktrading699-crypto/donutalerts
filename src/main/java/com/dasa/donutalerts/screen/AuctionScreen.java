package com.dasa.donutalerts.screen;

import com.dasa.donutalerts.Alerts;
import com.dasa.donutalerts.Config;
import com.dasa.donutalerts.Fmt;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AuctionScreen extends BaseScreen {
    private static final int COLS = 11, ROWS = 4, PER = COLS * ROWS;

    private static final class Entry {
        final Item item;
        final String lname;

        Entry(Item item) {
            this.item = item;
            this.lname = item.getName().getString().toLowerCase(Locale.ROOT);
        }
    }

    private static List<Entry> ALL;

    private static List<Entry> all() {
        if (ALL == null) {
            ALL = new ArrayList<>();
            for (Item i : Registries.ITEM) {
                if (i != Items.AIR) ALL.add(new Entry(i));
            }
        }
        return ALL;
    }

    private final List<Entry> view = new ArrayList<>();
    private int page = 0;
    private int L;
    private TextFieldWidget search, qtyField, timeField, minField;

    public AuctionScreen() {
        super(Text.literal("Auction"));
        // Opening the menu auto-picks the item (and stack size) you're holding.
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null && !mc.player.getMainHandStack().isEmpty()) {
            ItemStack s = mc.player.getMainHandStack();
            Alerts.cfg.auctionItemId = Registries.ITEM.getId(s.getItem()).toString();
            Alerts.cfg.auctionQty = s.getCount();
        }
    }

    private void refreshView(String q) {
        view.clear();
        String needle = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        for (Entry e : all()) {
            if (needle.isEmpty() || e.lname.contains(needle)) view.add(e);
        }
        page = 0;
    }

    private int pages() {
        return Math.max(1, (view.size() + PER - 1) / PER);
    }

    @Override
    protected void init() {
        Config c = Alerts.cfg;
        L = width / 2 - 110;

        refreshView("");

        // ---- search (the item you hold is already selected; search to pick a different one)
        search = new TextFieldWidget(textRenderer, L, 42, 220, 16, Text.literal("search"));
        search.setPlaceholder(Text.literal("Search items..."));
        search.setChangedListener(this::refreshView);
        addDrawableChild(search);

        // ---- item grid (fixed buttons; icons are drawn in render)
        for (int r = 0; r < ROWS; r++) {
            for (int col = 0; col < COLS; col++) {
                int idx = r * COLS + col;
                addDrawableChild(ButtonWidget.builder(Text.empty(), b -> {
                    int i = page * PER + idx;
                    if (i < view.size()) c.auctionItemId = Registries.ITEM.getId(view.get(i).item).toString();
                }).dimensions(L + col * 20, 62 + r * 20, 20, 20).build());
            }
        }

        addDrawableChild(ButtonWidget.builder(Text.literal("<"), b -> {
            if (page > 0) page--;
        }).dimensions(L, 146, 30, 18).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(">"), b -> {
            if (page + 1 < pages()) page++;
        }).dimensions(L + 190, 146, 30, 18).build());

        // ---- amount / time / min bid
        qtyField = new TextFieldWidget(textRenderer, L, 180, 64, 16, Text.literal("amount"));
        qtyField.setMaxLength(5);
        qtyField.setText(String.valueOf(c.auctionQty));
        qtyField.setChangedListener(s -> {
            try {
                int v = Integer.parseInt(s.trim());
                if (v > 0) c.auctionQty = v;
            } catch (NumberFormatException ignored) {}
        });
        addDrawableChild(qtyField);

        timeField = new TextFieldWidget(textRenderer, L + 72, 180, 70, 16, Text.literal("time"));
        timeField.setMaxLength(8);
        timeField.setText(c.auctionTime);
        timeField.setChangedListener(s -> {
            if (Fmt.parseDuration(s) > 0) c.auctionTime = s.trim();
        });
        addDrawableChild(timeField);

        minField = new TextFieldWidget(textRenderer, L + 150, 180, 70, 16, Text.literal("min"));
        minField.setMaxLength(12);
        minField.setText(Fmt.shortNum(c.auctionMin));
        minField.setChangedListener(s -> {
            Double v = Fmt.parseLoose(s);
            if (v != null && v >= 0) c.auctionMin = v;
        });
        addDrawableChild(minField);

        // ---- start / stop / back
        addDrawableChild(ButtonWidget.builder(Text.literal("START"), b -> {
            Alerts.startAuction();
            close();
        }).dimensions(L, 202, 108, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("STOP"), b -> Alerts.stopAuction())
                .dimensions(L + 112, 202, 108, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Back"), b -> client.setScreen(new MainScreen()))
                .dimensions(L, 226, 220, 20).build());
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        Config c = Alerts.cfg;

        ctx.drawText(textRenderer, "BIDDING - AUCTION", L, 8, 0xFFFFAA00, true);

        // selected item
        ctx.drawItem(Alerts.itemStack(c.auctionItemId), L + 2, 21);
        ctx.drawText(textRenderer, Alerts.itemName(c.auctionItemId), L + 24, 25, 0xFFFFFFFF, true);

        // grid icons
        for (int r = 0; r < ROWS; r++) {
            for (int col = 0; col < COLS; col++) {
                int i = page * PER + r * COLS + col;
                if (i >= view.size()) continue;
                ctx.drawItem(new ItemStack(view.get(i).item), L + col * 20 + 2, 62 + r * 20 + 2);
            }
        }

        ctx.drawCenteredTextWithShadow(textRenderer,
                Text.literal("Page " + (page + 1) + " / " + pages()), L + 110, 151, 0xFFAAAAAA);

        ctx.drawText(textRenderer, "Amount", L, 170, 0xFF5599FF, true);
        ctx.drawText(textRenderer, "Time (5m,1h)", L + 72, 170, 0xFF5599FF, true);
        ctx.drawText(textRenderer, "Min bid (1m)", L + 150, 170, 0xFF5599FF, true);

        ctx.drawText(textRenderer, Alerts.statusLine(), L, 250, 0xFFAAAAAA, false);
    }
}
