package com.mlpgod.mafiatool.screen;

import com.mlpgod.mafiatool.model.GameRecord;
import com.mlpgod.mafiatool.model.GamePlayer;
import com.mlpgod.mafiatool.model.PlayerStatus;
import com.mlpgod.mafiatool.service.GameService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public final class PlayerStatusScreen extends Screen {
    private final GameRecord game; private final GamePlayer player;
    private final List<GamePlayer> possibleActors;
    private int selectedActorIndex = -1;
    private int actorScroll;
    private EditBox reason; private String error = "";
    public PlayerStatusScreen(GameRecord game, GamePlayer player) {
        super(Component.literal("Статус игрока"));
        this.game = game;
        this.player = player;
        this.possibleActors = game.players.stream()
                .filter(p -> p.status == PlayerStatus.ALIVE && !p.id.equals(player.id)).toList();
    }
    @Override protected void init() {
        int left = width / 2 - 170;
        int listY = 92;
        int rows = visibleActorRows();
        int optionCount = possibleActors.size() + 1;
        actorScroll = Math.max(0, Math.min(actorScroll, Math.max(0, optionCount - rows)));
        List<Button> actorButtons = new ArrayList<>();
        for (int row = 0; row < rows && actorScroll + row < optionCount; row++) {
            int option = actorScroll + row;
            String name = option == 0 ? "Без исполнителя" : possibleActors.get(option - 1).name;
            Button button = Button.builder(Component.literal(actorOptionLabel(option)), b -> {
                selectedActorIndex = option - 1;
                for (int i = 0; i < actorButtons.size(); i++) actorButtons.get(i).setMessage(Component.literal(actorOptionLabel(actorScroll + i)));
            }).bounds(left, listY + row * 24, 340, 22).build();
            addRenderableWidget(button);
            actorButtons.add(button);
        }
        int reasonY = listY + rows * 24 + 8;
        reason = new EditBox(font, left, reasonY, 340, 20, Component.literal("Причина"));
        reason.setHint(Component.literal("Причина или пояснение (необязательно)")); reason.setMaxLength(120); addRenderableWidget(reason);
        int buttonsY = reasonY + 30;
        addRenderableWidget(Button.builder(Component.literal("Жив"), b -> set(PlayerStatus.ALIVE)).bounds(left, buttonsY, 76, 22).build());
        addRenderableWidget(Button.builder(Component.literal("Мёртв"), b -> set(PlayerStatus.DEAD)).bounds(left + 82, buttonsY, 76, 22).build());
        addRenderableWidget(Button.builder(Component.literal("Исключён"), b -> set(PlayerStatus.EXCLUDED)).bounds(left + 164, buttonsY, 92, 22).build());
        addRenderableWidget(Button.builder(Component.literal("Отмена"), b -> Minecraft.getInstance().setScreen(new MafiaGameScreen(game))).bounds(left + 262, buttonsY, 78, 22).build());
    }
    private String actorOptionLabel(int option) {
        String name = option == 0 ? "Без исполнителя" : possibleActors.get(option - 1).name;
        return (selectedActorIndex == option - 1 ? "[x] " : "[ ] ") + name;
    }
    private void set(PlayerStatus status) {
        if (status != PlayerStatus.ALIVE && player.status != status) {
            Minecraft.getInstance().setScreen(new ConfirmScreen(yes -> {
                if (yes) apply(status);
                else Minecraft.getInstance().setScreen(this);
            }, Component.literal(status == PlayerStatus.DEAD ? "Отметить погибшим?" : "Исключить из партии?"), Component.literal(player.name + " останется в списке, а действие попадёт в журнал."), Component.literal("Подтвердить"), Component.literal("Отмена")));
            return;
        }
        apply(status);
    }
    private void apply(PlayerStatus status) {
        try {
            String actorId = status == PlayerStatus.DEAD && selectedActorIndex >= 0 ? possibleActors.get(selectedActorIndex).id : null;
            GameService.get().setPlayerStatus(game, player.id, status, actorId, reason.getValue());
            Minecraft.getInstance().setScreen(new MafiaGameScreen(game));
        }
        catch (Exception e) { error = e.getMessage(); }
    }
    private int visibleActorRows() { return Math.max(3, Math.min(10, (height - 200) / 24)); }
    @Override public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int left = width / 2 - 170;
        int listBottom = 92 + visibleActorRows() * 24;
        int maxScroll = Math.max(0, possibleActors.size() + 1 - visibleActorRows());
        if (mouseX >= left && mouseX <= left + 340 && mouseY >= 92 && mouseY < listBottom && maxScroll > 0) {
            actorScroll = Math.max(0, Math.min(maxScroll, actorScroll - (int) Math.signum(verticalAmount)));
            String reasonValue = reason == null ? "" : reason.getValue();
            rebuildWidgets();
            if (reason != null) reason.setValue(reasonValue);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }
    @Override public void render(GuiGraphics g, int x, int y, float d) {
        g.fill(0, 0, width, height, 0xA0101010);
        g.drawCenteredString(font, "Статус: " + player.name, width / 2, 28, 0xFFFFFF);
        g.drawCenteredString(font, "Исполнитель убийства (необязательно)", width / 2, 52, 0xA0A0A0);
        if (possibleActors.size() + 1 > visibleActorRows())
            g.drawCenteredString(font, "Колёсико мыши — прокрутка списка", width / 2, 72, 0x909090);
        if (!error.isBlank()) g.drawCenteredString(font, error, width / 2, height - 30, 0xFF8888);
        super.render(g, x, y, d);
    }
    @Override public boolean isPauseScreen() { return false; }
}
