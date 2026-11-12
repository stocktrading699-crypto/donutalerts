package com.dasa.donutalerts.screen;

import com.dasa.donutalerts.Alerts;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

public abstract class BaseScreen extends Screen {
    protected BaseScreen(Text title) {
        super(title);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        Alerts.renderAll(ctx); // so test alerts show while the menu is open
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void removed() {
        Alerts.cfg.save();
    }
}
