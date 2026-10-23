package com.mlpgod.mafiatool.screen;

import com.mlpgod.mafiatool.model.*;
import com.mlpgod.mafiatool.service.GameService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Action attempt and outcome are separate fields. No action here changes a player's status. */
public final class EventEntryScreen extends Screen {
    private final GameRecord game;
    private final List<GamePlayer> active;
    private final Screen returnTo;
    private int actorIndex = -1;
    private int actorScroll;
    private int targetIndex = -1;
    private int targetScroll;
    private int typeIndex;
    private EditBox description;
    private EditBox result;
    private String error = "";

    public EventEntryScreen(GameRecord game) {
        super(Component.literal("Записать действие"));
        this.game = game;
        this.returnTo = new MafiaGameScreen(game);
        this.active = game.players.stream().filter(p -> p.status == PlayerStatus.ALIVE).toList();
    }

    @Override protected void init() {
        int panelWidth = Math.min(520, width - 32);
        int left = width - panelWidth - 16;
        int listY = 80;
        int columnWidth = (panelWidth - 36) / 2;
        int rows = visibleRows();
        int shownActorRows = Math.min(rows, Math.max(1, active.size()));
        actorScroll = Math.max(0, Math.min(actorScroll, Math.max(0, active.size() - rows)));
        targetScroll = Math.max(0, Math.min(targetScroll, Math.max(0, active.size() + 1 - rows)));
        List<Button> actorButtons = new ArrayList<>();
        for (int row = 0; row < shownActorRows && actorScroll + row < active.size(); row++) {
            int option = actorScroll + row;
            Button button = Button.builder(Component.literal(actorOptionLabel(option)), b -> {
                actorIndex = option;
                for (int i = 0; i < actorButtons.size(); i++)
                    actorButtons.get(i).setMessage(Component.literal(actorOptionLabel(actorScroll + i)));
            }).bounds(left + 12, listY + row * 24, columnWidth, 22).build();
            addRenderableWidget(button);
            actorButtons.add(button);
        }
        if (active.isEmpty()) addRenderableWidget(Button.builder(Component.literal("Нет живых игроков"), b -> {})
                .bounds(left + 12, listY, columnWidth, 22).build());

        List<Button> targetButtons = new ArrayList<>();
        int shownTargetRows = Math.min(rows, active.size() + 1);
        for (int row = 0; row < shownTargetRows; row++) {
            int option = targetScroll + row;
            Button button = Button.builder(Component.literal(targetOptionLabel(option)), b -> {
                targetIndex = option == 0 ? -1 : option - 1;
                for (int i = 0; i < targetButtons.size(); i++)
                    targetButtons.get(i).setMessage(Component.literal(targetOptionLabel(targetScroll + i)));
            }).bounds(left + 24 + columnWidth, listY + row * 24, columnWidth, 22).build();
            addRenderableWidget(button);
            targetButtons.add(button);
        }

        int controlsY = listY + rows * 24 + 12;
        addRenderableWidget(Button.builder(Component.literal("Тип действия: " + typeLabel()), b -> {
            typeIndex = (typeIndex + 1) % actionTypes().length;
            b.setMessage(Component.literal("Тип действия: " + typeLabel()));
        }).bounds(left + 12, controlsY, columnWidth, 22).build());
        description = edit(left + 12, controlsY + 30, panelWidth - 24, "Описание действия", 200);
        result = edit(left + 12, controlsY + 58, panelWidth - 24, "Результат: успешно / сорвано / спасён", 200);
        addRenderableWidget(Button.builder(Component.literal("Записать в журнал"), b -> save()).bounds(left + 12, controlsY + 90, 160, 22).build());
        addRenderableWidget(Button.builder(Component.literal("Отмена"), b -> Minecraft.getInstance().setScreen(returnTo)).bounds(left + 180, controlsY + 90, 86, 22).build());
    }

    private EditBox edit(int x, int y, int w, String hint, int max) {
        EditBox box = new EditBox(font, x, y, w, 20, Component.literal(hint)); box.setHint(Component.literal(hint)); box.setMaxLength(max); addRenderableWidget(box); return box;
    }
    private EventType[] actionTypes() { return new EventType[]{EventType.ACTION, EventType.KILL_ATTEMPT, EventType.CHECK, EventType.TREATMENT, EventType.CUSTOM_ACTION}; }
    private int visibleRows() { return Math.max(2, Math.min(6, (height - 290) / 24)); }
    private String actorOptionLabel(int option) {
        return (actorIndex == option ? "[x] " : "[ ] ") + active.get(option).name;
    }
    private String targetOptionLabel(int option) {
        if (option == 0) return (targetIndex < 0 ? "[x] " : "[ ] ") + "Без цели";
        return (targetIndex == option - 1 ? "[x] " : "[ ] ") + active.get(option - 1).name;
    }
    private String typeLabel() {
        return switch (actionTypes()[typeIndex]) {
            case ACTION -> "Произвольное действие";
            case KILL_ATTEMPT -> "Попытка убийства";
            case CHECK -> "Проверка";
            case TREATMENT -> "Лечение";
            default -> "Другое действие";
        };
    }

    private void save() {
        try {
            if (actorIndex < 0 || actorIndex >= active.size()) throw new IllegalStateException("Выберите игрока, который выполнил действие");
            String actorId = active.get(actorIndex).id;
            String targetId = targetIndex < 0 ? null : active.get(targetIndex).id;
            String text = description.getValue().isBlank() ? typeLabel() : description.getValue();
            GameService.get().recordAction(game, actionTypes()[typeIndex], actorId, targetId, text, result.getValue());
            Minecraft.getInstance().setScreen(returnTo);
        } catch (Exception e) { error = e.getMessage(); }
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int panelWidth = Math.min(520, width - 32);
        int left = width - panelWidth - 16;
        int columnWidth = (panelWidth - 36) / 2;
        int rows = visibleRows();
        int listBottom = 80 + rows * 24;
        int maxActorScroll = Math.max(0, active.size() - rows);
        int maxTargetScroll = Math.max(0, active.size() + 1 - rows);
        if (mouseY >= 80 && mouseY < listBottom && mouseX >= left + 12 && mouseX < left + 12 + columnWidth && maxActorScroll > 0) {
            actorScroll = Math.max(0, Math.min(maxActorScroll, actorScroll - (int) Math.signum(verticalAmount)));
            rebuildList(description, result);
            return true;
        }
        if (mouseY >= 80 && mouseY < listBottom && mouseX >= left + 24 + columnWidth && mouseX < left + 24 + 2 * columnWidth && maxTargetScroll > 0) {
            targetScroll = Math.max(0, Math.min(maxTargetScroll, targetScroll - (int) Math.signum(verticalAmount)));
            rebuildList(description, result);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private void rebuildList(EditBox previousDescription, EditBox previousResult) {
        String descriptionValue = previousDescription == null ? "" : previousDescription.getValue();
        String resultValue = previousResult == null ? "" : previousResult.getValue();
        rebuildWidgets();
        if (description != null) description.setValue(descriptionValue);
        if (result != null) result.setValue(resultValue);
    }

    @Override public void render(GuiGraphics g, int x, int y, float d) {
        int panelWidth = Math.min(520, width - 32);
        int left = width - panelWidth - 16;
        int columnWidth = (panelWidth - 36) / 2;
        g.fill(left, 16, left + panelWidth, height - 16, 0xE010141C);
        g.fill(left, 16, left + panelWidth, 18, 0xFF586D85);
        g.drawString(font, "Записать действие · " + game.phase, left + 12, 28, 0xFFFFFFFF);
        g.drawString(font, "Кто выполнил действие?", left + 12, 62, 0xFFFFFF);
        g.drawString(font, "Цель действия", left + 24 + columnWidth, 62, 0xFFFFFF);
        if (active.size() + 1 > visibleRows()) {
            g.drawString(font, "Колёсико прокручивает список под курсором", left + 12, 76, 0x909090);
        }
        if (!error.isBlank()) g.drawString(font, error, left + 12, height - 28, 0xFFFF8888);
        super.render(g, x, y, d);
    }
    @Override public boolean isPauseScreen() { return false; }
}
