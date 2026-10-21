package com.mlpgod.mafiatool.screen;

import com.mlpgod.mafiatool.model.*;
import com.mlpgod.mafiatool.service.GameService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Explicit review checkpoint before the one-time random assignment. */
public final class GameReviewScreen extends Screen {
    private final GameRecord game;
    private final Screen returnTo;
    private final GameService service = GameService.get();
    private PhaseKind initialPhase = PhaseKind.DAY;
    private String message = "";
    private int scroll;

    public GameReviewScreen(GameRecord game, Screen returnTo) {
        super(Component.literal("Проверка состава")); this.game = game; this.returnTo = returnTo;
    }

    @Override protected void init() {
        int left = Math.max(18, width / 2 - 230);
        int visible = Math.max(1, Math.min(12, (height - 196) / 24));
        scroll = Math.min(scroll, Math.max(0, game.players.size() - visible));
        for (int i = scroll; i < game.players.size() && i < scroll + visible; i++) {
            GamePlayer player = game.players.get(i);
            String role = player.role == null ? "Будет назначена случайно" : player.role.name;
            addRenderableWidget(Button.builder(Component.literal(player.name + " · " + role), b -> {})
                    .bounds(left, 96 + (i - scroll) * 24, 460, 21).build());
        }
        if (game.players.size() > visible) {
            addRenderableWidget(Button.builder(Component.literal("▲"), b -> { scroll = Math.max(0, scroll - 1); rebuildWidgets(); })
                    .bounds(left + 466, 80, 22, 20).build());
            addRenderableWidget(Button.builder(Component.literal("▼"), b -> { scroll = Math.min(game.players.size() - visible, scroll + 1); rebuildWidgets(); })
                    .bounds(left + 466, height - 70, 22, 20).build());
        }
        int bottom = height - 40;
        addRenderableWidget(Button.builder(Component.literal("Назад к участникам"), b -> Minecraft.getInstance().setScreen(returnTo))
                .bounds(left, bottom, 142, 24).build());
        if (!game.rolesAssigned) {
            addRenderableWidget(Button.builder(Component.literal("Распределить роли…"), b -> distribute())
                    .bounds(left + 150, bottom, 144, 24).build());
        } else {
            addRenderableWidget(Button.builder(Component.literal("Перераспределить…"), b -> redistribute())
                    .bounds(left + 150, bottom, 144, 24).build());
            addRenderableWidget(Button.builder(Component.literal("Начать: " + phaseName()), b -> {
                initialPhase = initialPhase == PhaseKind.DAY ? PhaseKind.NIGHT : PhaseKind.DAY;
                b.setMessage(Component.literal("Начать: " + phaseName()));
            }).bounds(left + 300, bottom, 108, 24).build());
            addRenderableWidget(Button.builder(Component.literal("Начать партию"), b -> start())
                    .bounds(left + 414, bottom, 118, 24).build());
        }
    }

    private void distribute() {
        List<String> errors = service.validationErrors(game);
        if (!errors.isEmpty()) {
            message = String.join(" · ", errors);
            rebuildWidgets();
            return;
        }
        try {
            service.distributeRoles(game);
            message = "Роли распределены и сохранены";
        } catch (Exception e) {
            message = e.getMessage() == null ? "Не удалось распределить роли" : e.getMessage();
        }
        rebuildWidgets();
    }

    private void redistribute() {
        Minecraft.getInstance().setScreen(new ConfirmScreen(yes -> {
            if (yes) try { service.redistributeRoles(game); message = "Раздача заменена"; }
            catch (Exception e) { message = e.getMessage(); }
            Minecraft.getInstance().setScreen(this); rebuildWidgets();
        }, Component.literal("Перераспределить роли?"), Component.literal("Текущая раздача будет заменена до начала партии."), Component.literal("Перераспределить"), Component.literal("Отмена")));
    }

    private void start() {
        try {
            service.startGame(game, initialPhase);
            Minecraft.getInstance().setScreen(new MafiaGameScreen(game));
        } catch (Exception e) { message = e.getMessage(); }
    }

    private String phaseName() { return initialPhase == PhaseKind.DAY ? "День" : "Ночь"; }

    @Override public void render(GuiGraphics g, int x, int y, float d) {
        g.fill(0, 0, width, height, 0xA0101010);
        g.drawCenteredString(font, "Проверка состава · " + game.title, width / 2, 18, 0xFFFFFF);
        int players = game.players.size(), total = service.enabledRoleCount(game);
        g.drawCenteredString(font, "Участников: " + players + " · выбранных ролей: " + total + (players == total ? " · совпадает" : " · не совпадает"), width / 2, 43,
                players == total ? 0xAADD99 : 0xFF8888);
        g.drawCenteredString(font, "Каждый шаблон роли будет скопирован в партию; последующие правки шаблонов не изменят эту раздачу.", width / 2, 61, 0xA0A0A0);
        String composition = game.rolePlan == null ? "Роли партии не настроены" : game.rolePlan.stream()
                .filter(selection -> selection.enabled)
                .map(selection -> selection.role.name + " × " + selection.count)
                .reduce((a, b) -> a + " · " + b).orElse("Нет включённых ролей");
        g.drawCenteredString(font, clip(composition, Math.max(80, width - 32)), width / 2, 77, 0xC0C0C0);
        if (game.rolesAssigned) g.drawCenteredString(font, "Роли уже распределены и сохранены", width / 2, height - 64, 0xAADD99);
        if (!message.isBlank()) {
            var lines = font.split(Component.literal(message), Math.max(80, width - 40));
            int y = Math.max(88, height - 82);
            int color = game.rolesAssigned ? 0xAADD99 : 0xFF8888;
            for (int i = 0; i < Math.min(lines.size(), 4); i++) {
                g.drawCenteredString(font, lines.get(i), width / 2, y + i * 10, color);
            }
        }
        super.render(g, x, y, d);
    }
    private String clip(String value, int pixelWidth) {
        int max = Math.max(12, pixelWidth / 6);
        return value.length() > max ? value.substring(0, max - 1) + "…" : value;
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        int visible = Math.max(1, Math.min(12, (height - 196) / 24));
        if (game.players.size() > visible && vertical != 0) {
            scroll = Math.max(0, Math.min(game.players.size() - visible, scroll - (int) Math.signum(vertical)));
            rebuildWidgets();
            return true;
        }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }

    @Override public boolean isPauseScreen() { return false; }
}
