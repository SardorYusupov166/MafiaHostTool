package com.mlpgod.mafiatool.screen;

import com.mlpgod.mafiatool.model.*;
import com.mlpgod.mafiatool.service.GameService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Arrays;
import java.util.List;

/** Persistent creation wizard: participants -> role plan -> review/distribute. */
public final class MafiaSetupScreen extends Screen {
    private final GameService service = GameService.get();
    private final GameRecord game;
    private EditBox title;
    private EditBox nameInput;
    private EditBox bulkInput;
    private int scroll;
    private String message = "";

    public MafiaSetupScreen(GameRecord game) {
        super(Component.literal("Подготовка партии"));
        this.game = game;
    }

    @Override protected void init() {
        int left = Math.max(10, width / 2 - 310);
        int panelWidth = Math.min(620, width - left - 18);
        title = new EditBox(font, left + 90, 47, Math.max(120, panelWidth - 90), 20, Component.literal("Название"));
        title.setMaxLength(48); title.setValue(game.title);
        title.setResponder(value -> { if (!value.isBlank() && !value.equals(game.title)) try { service.renameGame(game, value); } catch (Exception ignored) {} });
        addRenderableWidget(title);
        nameInput = new EditBox(font, left, 82, Math.min(245, panelWidth), 20, Component.literal("Ник игрока"));
        nameInput.setHint(Component.literal("Ник игрока")); nameInput.setMaxLength(32); addRenderableWidget(nameInput);
        addRenderableWidget(Button.builder(Component.literal("Добавить"), b -> addOne()).bounds(left + 251, 82, 82, 20).build());
        bulkInput = new EditBox(font, left, 111, Math.max(160, panelWidth - 92), 20, Component.literal("Список ников"));
        bulkInput.setHint(Component.literal("Список ников через запятую, ; или |")); bulkInput.setMaxLength(1600); addRenderableWidget(bulkInput);
        addRenderableWidget(Button.builder(Component.literal("Добавить список"), b -> addBulk()).bounds(left + panelWidth - 88, 111, 88, 20).build());

        int visible = Math.max(1, Math.min(10, (height - 262) / 25));
        scroll = Math.min(scroll, Math.max(0, game.players.size() - visible));
        for (int i = scroll; i < game.players.size() && i < scroll + visible; i++) {
            GamePlayer player = game.players.get(i);
            int y = 145 + (i - scroll) * 25;
            addRenderableWidget(Button.builder(Component.literal((i + 1) + ". " + player.name), b -> {})
                    .bounds(left, y, Math.max(160, panelWidth - 150), 21).build());
            addRenderableWidget(Button.builder(Component.literal("Переименовать"), b -> Minecraft.getInstance().setScreen(new RenamePlayerScreen(game, player, this)))
                    .bounds(left + panelWidth - 146, y, 92, 21).build());
            addRenderableWidget(Button.builder(Component.literal("×"), b -> removePlayer(player))
                    .bounds(left + panelWidth - 50, y, 28, 21).build());
        }
        if (game.players.size() > visible) {
            addRenderableWidget(Button.builder(Component.literal("▲"), b -> { scroll = Math.max(0, scroll - 1); rebuildWidgets(); })
                    .bounds(left + panelWidth - 24, 143, 22, 20).build());
            addRenderableWidget(Button.builder(Component.literal("▼"), b -> { scroll = Math.min(game.players.size() - visible, scroll + 1); rebuildWidgets(); })
                    .bounds(left + panelWidth - 24, 145 + visible * 25, 22, 20).build());
        }

        int bottom = height - 40;
        addRenderableWidget(Button.builder(Component.literal("Настроить роли"), b -> Minecraft.getInstance().setScreen(new RoleSettingsScreen(this)))
                .bounds(left, bottom, 112, 24).build());
        addRenderableWidget(Button.builder(Component.literal("Роли партии"), b -> Minecraft.getInstance().setScreen(new PartyRoleSelectionScreen(game, this)))
                .bounds(left + 118, bottom, 112, 24).build());
        addRenderableWidget(Button.builder(Component.literal("Проверка состава →"), b -> {
            if (!game.title.equals(title.getValue().trim())) try { service.renameGame(game, title.getValue()); } catch (Exception ignored) {}
            Minecraft.getInstance().setScreen(new GameReviewScreen(game, this));
        }).bounds(left + 236, bottom, 152, 24).build());
        addRenderableWidget(Button.builder(Component.literal("К списку"), b -> Minecraft.getInstance().setScreen(new MafiaGamesScreen()))
                .bounds(width - 94, height - 40, 76, 24).build());
    }

    private void addOne() {
        try { service.addPlayer(game, nameInput.getValue()); nameInput.setValue(""); message = "Игрок добавлен"; rebuildWidgets(); }
        catch (Exception e) { message = e.getMessage(); }
    }

    private void addBulk() {
        List<String> names = Arrays.stream(bulkInput.getValue().split("[,;|\\n\\r]+")).map(String::trim).filter(s -> !s.isBlank()).toList();
        try { service.addPlayers(game, names); bulkInput.setValue(""); message = "Добавлено игроков: " + names.size(); rebuildWidgets(); }
        catch (Exception e) { message = e.getMessage(); }
    }

    private void removePlayer(GamePlayer player) {
        try { service.removePreparingPlayer(game, player.id); message = "Игрок удалён из подготовки"; rebuildWidgets(); }
        catch (Exception e) { message = e.getMessage(); }
    }

    private void validate() {
        try {
            if (!game.title.equals(title.getValue().trim())) service.renameGame(game, title.getValue());
            List<String> errors = service.validationErrors(game);
            message = errors.isEmpty()
                    ? "Состав подходит: " + game.players.size() + " игроков, ролей с действиями: " + service.enabledRoleCount(game)
                        + (Math.max(0, game.players.size() - service.enabledRoleCount(game)) > 0
                            ? ", мирных автоматически: " + Math.max(0, game.players.size() - service.enabledRoleCount(game)) : "")
                    : String.join(" · ", errors);
        } catch (Exception e) { message = e.getMessage(); }
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, 0xA0101010);
        int left = Math.max(10, width / 2 - 310);
        g.drawCenteredString(font, "Подготовка партии", width / 2, 18, 0xFFFFFF);
        g.drawString(font, "Название:", left, 52, 0xD0D0D0);
        g.drawString(font, "Участники · " + game.players.size(), left, 69, 0xFFFFFF);
        g.drawString(font, "Добавить одного игрока или вставить список ников", left, 101, 0xAAAAAA);
        if (game.rolesAssigned) {
            g.drawString(font, "Раздача зафиксирована. Изменения шаблонов ролей её не затронут.", left, height - 64, 0xA0A0A0);
            int visible = Math.max(1, Math.min(10, (height - 262) / 25));
            for (int i = scroll; i < game.players.size() && i < scroll + visible; i++) {
                GamePlayer p = game.players.get(i);
                String role = p.role == null ? "—" : p.role.name + (p.role.team == null ? "" : " · " + p.role.team);
                g.drawString(font, role, width / 2 + 70, 150 + (i - scroll) * 25, 0xB0B0B0);
            }
        } else {
            int selected = service.enabledRoleCount(game);
            g.drawString(font, "Игроков: " + game.players.size() + " · ролей с действиями: " + selected
                    + " · мирных автоматически: " + Math.max(0, game.players.size() - selected), left, height - 64, 0xA0A0A0);
        }
        if (!message.isBlank()) g.drawString(font, message, left, height - 54, 0xFFFFFF);
        super.render(g, mouseX, mouseY, delta);
    }

    @Override public boolean mouseScrolled(double x, double y, double hx, double vy) {
        int visible = Math.max(1, Math.min(10, (height - 262) / 25));
        if (game.players.size() > visible) {
            scroll = Math.max(0, Math.min(game.players.size() - visible, scroll - (int) Math.signum(vy)));
            rebuildWidgets(); return true;
        }
        return super.mouseScrolled(x, y, hx, vy);
    }

    @Override public boolean isPauseScreen() { return false; }
}
