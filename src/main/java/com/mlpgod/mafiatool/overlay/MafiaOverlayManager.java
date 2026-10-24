package com.mlpgod.mafiatool.overlay;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mlpgod.mafiatool.model.GameEvent;
import com.mlpgod.mafiatool.model.GameRecord;
import com.mlpgod.mafiatool.model.GameStatus;
import com.mlpgod.mafiatool.service.GameService;
import com.mlpgod.mafiatool.screen.MafiaGameScreen;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * A passive HUD view of the current game, independent of Minecraft's Screen lifecycle.
 * Screens are only used to get a mouse cursor and interact with the log.
 */
public final class MafiaOverlayManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("mafiatool/overlay.json");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());
    private static final Settings SETTINGS = readSettings();
    private static GameRecord activeGame;

    private MafiaOverlayManager() {}

    public static void initialize() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("mafiatool", "active_game_overlay"),
                (graphics, tickCounter) -> renderHud(graphics));
    }

    /** Resolve a saved game by ID instead of persisting references to old Java objects. */
    public static GameRecord current() {
        if (SETTINGS.activeGameId == null || SETTINGS.activeGameId.isBlank()) return null;
        if (activeGame == null || !SETTINGS.activeGameId.equals(activeGame.id)) {
            activeGame = GameService.get().find(SETTINGS.activeGameId);
        }
        if (activeGame == null || activeGame.status != GameStatus.ACTIVE) {
            clearActive();
            return null;
        }
        return activeGame;
    }

    public static void show(GameRecord game) {
        if (game == null || game.status != GameStatus.ACTIVE) return;
        boolean newGame = !game.id.equals(SETTINGS.activeGameId);
        activeGame = game;
        SETTINGS.activeGameId = game.id;
        SETTINGS.visible = true;
        if (newGame) SETTINGS.collapsed = false;
        save();
    }

    public static void hide() {
        SETTINGS.visible = false;
        save();
    }

    public static void setCollapsed(boolean collapsed) {
        SETTINGS.collapsed = collapsed;
        save();
    }

    public static boolean isCollapsed() { return SETTINGS.collapsed; }
    public static boolean isVisible() { return SETTINGS.visible && current() != null; }

    public static void onGameRemoved(GameRecord game) {
        if (game != null && game.id.equals(SETTINGS.activeGameId)) clearActive();
    }

    private static void clearActive() {
        activeGame = null;
        SETTINGS.activeGameId = "";
        SETTINGS.visible = false;
        save();
    }

    public static int panelWidth(int screenW) {
        return Math.max(1, Math.min(SETTINGS.w, Math.max(1, screenW - 16)));
    }
    public static int panelHeight(int screenH) {
        return Math.max(1, Math.min(SETTINGS.h, Math.max(1, screenH - 16)));
    }
    public static int panelX(int screenW) {
        int w = panelWidth(screenW);
        int preferred = SETTINGS.x < 0 ? screenW - w - 16 : SETTINGS.x;
        return Math.max(0, Math.min(preferred, screenW - w));
    }
    public static int panelY(int screenH) {
        int h = SETTINGS.collapsed ? 40 : panelHeight(screenH);
        return Math.max(0, Math.min(SETTINGS.y, Math.max(0, screenH - h)));
    }
    public static void move(int x, int y, int screenW, int screenH) {
        int w = panelWidth(screenW);
        int h = SETTINGS.collapsed ? 40 : panelHeight(screenH);
        SETTINGS.x = Math.max(0, Math.min(x, screenW - w));
        SETTINGS.y = Math.max(0, Math.min(y, screenH - h));
    }
    public static void resize(int w, int h, int screenW, int screenH) {
        int availableWidth = Math.max(1, screenW - panelX(screenW) - 8);
        int availableHeight = Math.max(1, screenH - panelY(screenH) - 8);
        SETTINGS.w = Math.max(1, Math.min(Math.min(760, Math.max(Math.min(500, screenW - 16), w)), availableWidth));
        SETTINGS.h = Math.max(1, Math.min(Math.min(760, Math.max(Math.min(260, screenH - 16), h)), availableHeight));
    }

    public static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(SETTINGS), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // An unavailable config directory should not interrupt a live Mafia session.
        }
    }

    private static Settings readSettings() {
        try {
            if (Files.isRegularFile(FILE)) {
                Settings result = GSON.fromJson(Files.readString(FILE, StandardCharsets.UTF_8), Settings.class);
                if (result != null) {
                    result.w = Math.max(500, Math.min(760, result.w));
                    result.h = Math.max(260, Math.min(760, result.h));
                    return result;
                }
            }
        } catch (Exception ignored) {}
        return new Settings();
    }

    private static void renderHud(GuiGraphics graphics) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null || client.screen instanceof MafiaGameScreen || !SETTINGS.visible) return;
        GameRecord game = current();
        if (game == null) return;

        int sw = client.getWindow().getGuiScaledWidth();
        int sh = client.getWindow().getGuiScaledHeight();
        int x = panelX(sw), y = panelY(sh);
        int w = panelWidth(sw), h = SETTINGS.collapsed ? 40 : panelHeight(sh);
        graphics.fill(x, y, x + w, y + h, 0xC410141C);
        graphics.fill(x, y, x + w, y + 2, 0xFF667D9C);
        graphics.drawString(client.font, clip(game.title, w - 24), x + 10, y + 8, 0xFFFFFFFF);
        graphics.drawString(client.font, game.phase + "  ·  Живы " + game.aliveCount() + "/" + game.players.size(),
                x + 10, y + 22, 0xFFB8D2A5);
        if (SETTINGS.collapsed) return;

        graphics.fill(x + 8, y + 40, x + w - 8, y + 42, 0xFF303C4B);
        graphics.drawString(client.font, "Журнал · " + game.events.size(), x + 10, y + 49, 0xFFE9EFF6);

        int lines = Math.max(0, Math.min(game.events.size(), (h - 111) / 31));
        int start = game.events.size() - lines;
        for (int i = start; i < game.events.size(); i++) {
            GameEvent event = game.events.get(i);
            int row = y + 68 + (i - start) * 31;
            String target = event.targetLabel == null || event.targetLabel.isBlank() ? "" : " → " + event.targetLabel;
            String first = TIME.format(Instant.ofEpochMilli(event.timestamp)) + " · " + event.phase + " · " + event.actorLabel + target;
            String second = event.description == null ? "" : event.description;
            if (event.result != null && !event.result.isBlank()) second += " · " + event.result;
            graphics.drawString(client.font, clip(first, w - 24), x + 10, row, 0xFFE8EDF4);
            graphics.drawString(client.font, clip(second, w - 24), x + 10, row + 12, 0xFFBAC6D5);
        }
        graphics.fill(x + 8, y + h - 30, x + w - 8, y + h - 29, 0xFF303C4B);
        graphics.drawString(client.font, "M — действия и управление  ·  Shift+M — партии", x + 10, y + h - 21, 0xFFA9B9CC);
    }

    private static String clip(String value, int pixels) {
        if (value == null) return "";
        int limit = Math.max(1, pixels / 6);
        return value.length() > limit ? value.substring(0, Math.max(0, limit - 1)) + "…" : value;
    }

    private static final class Settings {
        String activeGameId = "";
        boolean visible = false;
        boolean collapsed = false;
        int x = -1;
        int y = 16;
        int w = 520;
        int h = 480;
    }
}
