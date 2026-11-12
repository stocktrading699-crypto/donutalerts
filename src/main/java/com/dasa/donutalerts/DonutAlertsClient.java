package com.dasa.donutalerts;

import com.dasa.donutalerts.screen.AuctionScreen;
import com.dasa.donutalerts.screen.BaseScreen;
import com.dasa.donutalerts.screen.HudEditScreen;
import com.dasa.donutalerts.screen.MainScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.function.Supplier;

public class DonutAlertsClient implements ClientModInitializer {
    // Commands run while the chat box is open, so the screen is opened on the next tick.
    private static Supplier<Screen> pendingScreen;

    @Override
    public void onInitializeClient() {
        KeyBinding menuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.donutalerts.menu", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_K, "category.donutalerts"));
        KeyBinding moveKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.donutalerts.move", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_J, "category.donutalerts"));
        KeyBinding auctionKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.donutalerts.auction", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_L, "category.donutalerts"));

        // "X paid you $1M" arrives as a server (game) message
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> Alerts.onMessage(message));
        ClientReceiveMessageEvents.CHAT.register((message, signed, sender, params, ts) -> Alerts.onMessage(message));

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            // /donutalerts          -> main menu
            // /donutalerts move     -> move boxes
            // /donutalerts auction  -> auction menu
            dispatcher.register(ClientCommandManager.literal("donutalerts")
                    .executes(c -> {
                        pendingScreen = MainScreen::new;
                        return 1;
                    })
                    .then(ClientCommandManager.literal("move").executes(c -> {
                        pendingScreen = HudEditScreen::new;
                        return 1;
                    }))
                    .then(ClientCommandManager.literal("auction").executes(c -> {
                        pendingScreen = AuctionScreen::new;
                        return 1;
                    })));

            // /bidding        -> auction menu (auto-fills the item you're holding)
            // /bidding stop   -> stop / clear the auction
            dispatcher.register(ClientCommandManager.literal("bidding")
                    .executes(c -> {
                        pendingScreen = AuctionScreen::new;
                        return 1;
                    })
                    .then(ClientCommandManager.literal("stop").executes(c -> {
                        Alerts.stopAuction();
                        c.getSource().sendFeedback(Text.literal("Auction stopped"));
                        return 1;
                    })));
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (menuKey.wasPressed()) client.setScreen(new MainScreen());
            while (moveKey.wasPressed()) client.setScreen(new HudEditScreen());
            while (auctionKey.wasPressed()) client.setScreen(new AuctionScreen());
            if (pendingScreen != null) {
                Supplier<Screen> s = pendingScreen;
                pendingScreen = null;
                client.setScreen(s.get());
            }
        });

        HudRenderCallback.EVENT.register((ctx, tickCounter) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.currentScreen instanceof BaseScreen) return; // the screen draws it itself
            if (mc.options.hudHidden) return;
            Alerts.renderAll(ctx);
        });
    }
}
