package com.mlpgod.mafiatool;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public final class MafiaHostScreen extends Screen {
    private static final int MAX_PLAYERS = 50;
    private static final int MAX_ROLE_NAME = 32;
    private final Map<String, RoleOption> roles = new LinkedHashMap<>();
    private final List<PlayerAssignment> assignments = new ArrayList<>();
    private final Map<String, EditBox> countBoxes = new LinkedHashMap<>();
    private final Path roleFile = FabricLoader.getInstance().getConfigDir().resolve("mafiatool-roles.txt");
    private EditBox playerCountBox;
    private EditBox nicknamesBox;
    private EditBox newRoleBox;
    private String playersInput = "10";
    private String nicknamesInput = "";
    private String status = "Добавьте роли или используйте примеры";
    private int scroll;
    private int resultScroll;

    public MafiaHostScreen() {
        super(Component.literal("Ведущий мафии"));
        loadRoles();
    }

    private void loadRoles() {
        try {
            if (Files.exists(roleFile)) {
                for (String line : Files.readAllLines(roleFile, StandardCharsets.UTF_8)) {
                    String[] parts = line.split("\\t", -1);
                    if (parts.length != 3) continue;
                    String name = new String(Base64.getDecoder().decode(parts[0]), StandardCharsets.UTF_8);
                    if (name.isBlank() || name.length() > MAX_ROLE_NAME) continue;
                    roles.put(name, new RoleOption(Boolean.parseBoolean(parts[1]), Math.max(1, Integer.parseInt(parts[2]))));
                }
            }
        } catch (Exception e) {
            roles.clear();
            status = "Не удалось прочитать сохранённые роли";
        }
        if (roles.isEmpty()) {
            roles.put("Мафия", new RoleOption(true, 2));
            roles.put("Доктор", new RoleOption(true, 1));
            roles.put("Комиссар", new RoleOption(true, 1));
        }
    }

    private void saveRoles() {
        try {
            for (Map.Entry<String, EditBox> entry : countBoxes.entrySet()) {
                RoleOption option = roles.get(entry.getKey());
                if (option != null && !entry.getValue().getValue().isBlank()) {
                    try { option.count = Math.max(1, Integer.parseInt(entry.getValue().getValue())); }
                    catch (NumberFormatException ignored) { }
                }
            }
            Files.createDirectories(roleFile.getParent());
            List<String> lines = new ArrayList<>();
            for (Map.Entry<String, RoleOption> entry : roles.entrySet()) {
                String encodedName = Base64.getEncoder().encodeToString(entry.getKey().getBytes(StandardCharsets.UTF_8));
                lines.add(encodedName + "\t" + entry.getValue().enabled + "\t" + entry.getValue().count);
            }
            Files.write(roleFile, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            status = "Не удалось сохранить список ролей";
        }
    }

    @Override
    protected void init() {
        int left = Math.max(14, width / 2 - 210);
        int top = 45;
        playerCountBox = new EditBox(font, left + 138, top, 54, 20, Component.literal("Игроки"));
        playerCountBox.setMaxLength(2);
        playerCountBox.setFilter(s -> s.isEmpty() || s.chars().allMatch(Character::isDigit));
        playerCountBox.setValue(playersInput);
        playerCountBox.setResponder(value -> playersInput = value);
        addRenderableWidget(playerCountBox);

        nicknamesBox = new EditBox(font, left, top + 27, 228, 20, Component.literal("Ники игроков"));
        nicknamesBox.setHint(Component.literal("Ники через запятую (необязательно)"));
        nicknamesBox.setMaxLength(1600);
        nicknamesBox.setValue(nicknamesInput);
        nicknamesBox.setResponder(value -> nicknamesInput = value);
        addRenderableWidget(nicknamesBox);

        newRoleBox = new EditBox(font, left, top + 68, 156, 20, Component.literal("Название новой роли"));
        newRoleBox.setHint(Component.literal("Новая роль…"));
        newRoleBox.setMaxLength(MAX_ROLE_NAME);
        addRenderableWidget(newRoleBox);
        addRenderableWidget(Button.builder(Component.literal("Добавить"), button -> addRole())
                .bounds(left + 162, top + 68, 76, 20).build());

        countBoxes.clear();
        int rowY = top + 140;
        int rowHeight = 24;
        int visibleRows = Math.max(1, (height - 260) / rowHeight);
        List<Map.Entry<String, RoleOption>> entries = new ArrayList<>(roles.entrySet());
        scroll = Math.min(scroll, Math.max(0, entries.size() - visibleRows));
        for (int i = scroll; i < entries.size() && i < scroll + visibleRows; i++) {
            Map.Entry<String, RoleOption> entry = entries.get(i);
            RoleOption option = entry.getValue();
            String roleName = entry.getKey();
            int y = rowY + (i - scroll) * rowHeight;
            addRenderableWidget(Button.builder(toggleLabel(roleName, option), button -> {
                option.enabled = !option.enabled;
                button.setMessage(toggleLabel(roleName, option));
                saveRoles();
            }).bounds(left, y, 150, 20).build());
            EditBox count = new EditBox(font, left + 156, y, 42, 20, Component.literal("Количество"));
            count.setMaxLength(2);
            count.setFilter(s -> s.isEmpty() || s.chars().allMatch(Character::isDigit));
            count.setValue(Integer.toString(option.count));
            count.setResponder(value -> {
                if (!value.isBlank()) {
                    try {
                        int parsed = Integer.parseInt(value);
                        if (parsed > 0 && parsed <= MAX_PLAYERS) option.count = parsed;
                    } catch (NumberFormatException ignored) { }
                }
            });
            countBoxes.put(roleName, count);
            addRenderableWidget(count);
            addRenderableWidget(Button.builder(Component.literal("×"), button -> {
                roles.remove(roleName);
                saveRoles();
                refreshWidgets();
            }).bounds(left + 202, y, 26, 20).build());
        }
        if (entries.size() > visibleRows) {
            addRenderableWidget(Button.builder(Component.literal("▲"), button -> {
                scroll = Math.max(0, scroll - 1);
                refreshWidgets();
            }).bounds(left + 204, top + 96, 24, 20).build());
            addRenderableWidget(Button.builder(Component.literal("▼"), button -> {
                scroll = Math.min(entries.size() - visibleRows, scroll + 1);
                refreshWidgets();
            }).bounds(left + 204, height - 72, 24, 20).build());
        }

        if (!assignments.isEmpty()) {
            int resultX = resultX();
            int nameWidth = nameFieldWidth(resultX);
            int visibleAssignments = visibleAssignmentRows();
            resultScroll = Math.min(resultScroll, Math.max(0, assignments.size() - visibleAssignments));
            for (int i = resultScroll; i < assignments.size() && i < resultScroll + visibleAssignments; i++) {
                PlayerAssignment assignment = assignments.get(i);
                int y = 72 + (i - resultScroll) * 23;
                EditBox nameBox = new EditBox(font, resultX, y, nameWidth, 20, Component.literal("Ник игрока"));
                nameBox.setMaxLength(32);
                nameBox.setValue(assignment.name);
                nameBox.setResponder(value -> assignment.name = value);
                addRenderableWidget(nameBox);
            }
        }

        int buttonY = height - 42;
        addRenderableWidget(Button.builder(Component.literal("Раздать роли"), button -> distribute())
                .bounds(left, buttonY, 112, 22).build());
        addRenderableWidget(Button.builder(Component.literal("Скопировать"), button -> copyResults())
                .bounds(left + 118, buttonY, 94, 22).build());
        addRenderableWidget(Button.builder(Component.literal("Готово"), button -> onClose())
                .bounds(width - 78, buttonY, 60, 22).build());
    }

    private int resultX() {
        return Math.min(width - 255, width / 2 + 48);
    }

    private int nameFieldWidth(int resultX) {
        return Math.max(70, Math.min(120, (width - resultX - 28) / 2));
    }

    private int visibleAssignmentRows() {
        return Math.max(0, (height - 125) / 23);
    }

    private void refreshWidgets() {
        clearWidgets();
        init();
    }

    private void addRole() {
        String name = newRoleBox.getValue().trim();
        if (name.isEmpty()) {
            status = "Введите название роли";
            return;
        }
        if (name.equalsIgnoreCase("Мирный житель")) {
            status = "Роль «Мирный житель» добавляется автоматически";
            return;
        }
        if (roles.containsKey(name)) {
            status = "Такая роль уже есть";
            return;
        }
        if (roles.size() >= 40) {
            status = "Можно сохранить не больше 40 ролей";
            return;
        }
        roles.put(name, new RoleOption(false, 1));
        assignments.clear();
        saveRoles();
        status = "Роль «" + name + "» добавлена и сохранена";
        refreshWidgets();
    }

    private Component toggleLabel(String role, RoleOption option) {
        String shown = role.length() > 19 ? role.substring(0, 18) + "…" : role;
        return Component.literal((option.enabled ? "[X] " : "[ ] ") + shown);
    }

    private List<String> parseNicknames() {
        List<String> names = new ArrayList<>();
        for (String name : nicknamesInput.split("[,;|]")) {
            String trimmed = name.trim();
            if (!trimmed.isEmpty()) names.add(trimmed);
        }
        return names;
    }

    private void distribute() {
        int players;
        try {
            players = Integer.parseInt(playerCountBox.getValue());
        } catch (NumberFormatException e) {
            status = "Введите число игроков от 1 до " + MAX_PLAYERS;
            return;
        }
        if (players < 1 || players > MAX_PLAYERS) {
            status = "Введите число игроков от 1 до " + MAX_PLAYERS;
            return;
        }
        List<String> names = parseNicknames();
        if (names.size() > players) {
            status = "Ников больше, чем указано игроков";
            return;
        }

        List<String> shuffledRoles = new ArrayList<>();
        int selectedCount = 0;
        for (Map.Entry<String, RoleOption> entry : roles.entrySet()) {
            RoleOption option = entry.getValue();
            EditBox box = countBoxes.get(entry.getKey());
            if (box != null && !box.getValue().isBlank()) {
                try {
                    option.count = Integer.parseInt(box.getValue());
                } catch (NumberFormatException e) {
                    status = "Проверьте количество роли «" + entry.getKey() + "»";
                    return;
                }
            }
            if (!option.enabled) continue;
            if (option.count < 1 || option.count > players) {
                status = "Количество роли «" + entry.getKey() + "» должно быть от 1 до " + players;
                return;
            }
            selectedCount += option.count;
            for (int i = 0; i < option.count; i++) shuffledRoles.add(entry.getKey());
        }
        if (selectedCount > players) {
            status = "Включённых ролей больше, чем игроков";
            return;
        }
        for (int i = selectedCount; i < players; i++) shuffledRoles.add("Мирный житель");
        Collections.shuffle(shuffledRoles, new Random());

        assignments.clear();
        for (int i = 0; i < players; i++) {
            String name = i < names.size() ? names.get(i) : "Игрок " + (i + 1);
            assignments.add(new PlayerAssignment(name, shuffledRoles.get(i)));
        }
        saveRoles();
        resultScroll = 0;
        status = "Готово: распределено игроков — " + players;
        refreshWidgets();
    }

    private void copyResults() {
        if (assignments.isEmpty()) {
            status = "Сначала нажмите «Раздать роли»";
            return;
        }
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < assignments.size(); i++) {
            PlayerAssignment assignment = assignments.get(i);
            String name = assignment.name.isBlank() ? "Игрок " + (i + 1) : assignment.name;
            lines.add(name + " — " + assignment.role);
        }
        Minecraft.getInstance().keyboardHandler.setClipboard(String.join("\n", lines));
        status = "Результат скопирован в буфер обмена";
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        renderBackground(graphics, mouseX, mouseY, delta);
        graphics.drawCenteredString(font, "Ведущий мафии", width / 2, 18, 0xFFFFFF);
        int left = Math.max(14, width / 2 - 210);
        graphics.drawString(font, "Игроки:", left, 51, 0xD0D0D0);
        graphics.drawString(font, "Ники через запятую:", left, 63, 0xD0D0D0);
        graphics.drawString(font, "Создать роль:", left, 106, 0xD0D0D0);
        graphics.drawString(font, "Список ролей (количество):", left, 178, 0xD0D0D0);
        graphics.drawString(font, "Включённые роли раздаются случайно; остаток — мирные", left, height - 63, 0xA0A0A0);
        graphics.drawString(font, status, left, height - 53, 0xFFFFFF);
        int resultX = resultX();
        graphics.drawString(font, "Распределение для ведущего", resultX, 49, 0xFFFFFF);
        int nameWidth = nameFieldWidth(resultX);
        int roleX = resultX + nameWidth + 8;
        for (int i = resultScroll; i < assignments.size() && i < resultScroll + visibleAssignmentRows(); i++) {
            PlayerAssignment assignment = assignments.get(i);
            graphics.drawString(font, assignment.role, roleX, 78 + (i - resultScroll) * 23, 0xE0E0E0);
        }
        super.render(graphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int visibleRows = Math.max(1, (height - 260) / 24);
        if (mouseX < width / 2 && roles.size() > visibleRows) {
            scroll = Math.max(0, Math.min(roles.size() - visibleRows, scroll - (int) Math.signum(verticalAmount)));
            refreshWidgets();
            return true;
        }
        int visibleResults = visibleAssignmentRows();
        if (mouseX >= width / 2 && assignments.size() > visibleResults) {
            resultScroll = Math.max(0, Math.min(assignments.size() - visibleResults,
                    resultScroll - (int) Math.signum(verticalAmount)));
            refreshWidgets();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void onClose() {
        saveRoles();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static final class RoleOption {
        private boolean enabled;
        private int count;

        private RoleOption(boolean enabled, int count) {
            this.enabled = enabled;
            this.count = count;
        }
    }

    private static final class PlayerAssignment {
        private String name;
        private final String role;

        private PlayerAssignment(String name, String role) {
            this.name = name;
            this.role = role;
        }
    }
}
