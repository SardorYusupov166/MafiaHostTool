package com.mlpgod.mafiatool.screen;

import com.mlpgod.mafiatool.assistant.AiAssistantConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Local endpoint/model credentials. This screen never writes settings to the repository. */
final class AiAssistantSettingsScreen extends Screen {
    private final Screen returnTo;
    private EditBox endpoint;
    private EditBox model;
    private EditBox apiKey;
    private String status = "";

    AiAssistantSettingsScreen(Screen returnTo) {
        super(Component.literal("Настройки ИИ"));
        this.returnTo = returnTo;
    }

    @Override protected void init() {
        AiAssistantConfig config = AiAssistantConfig.load();
        int w = Math.min(440, width - 32);
        int x = (width - w) / 2;
        int y = Math.max(34, height / 2 - 106);
        endpoint = field(x, y + 28, w, "Адрес API", config.endpoint, 500);
        model = field(x, y + 76, w, "Модель", config.model, 120);
        apiKey = field(x, y + 124, w, "API-ключ", config.apiKey, 500);
        apiKey.setSuggestion(Component.literal("Ключ хранится локально в config/mafiatool/ai-assistant.json"));
        addRenderableWidget(Button.builder(Component.literal("Сохранить"), b -> save())
                .bounds(x + w - 174, y + 168, 82, 22).build());
        addRenderableWidget(Button.builder(Component.literal("Назад"), b -> Minecraft.getInstance().setScreen(returnTo))
                .bounds(x + w - 84, y + 168, 72, 22).build());
    }

    private EditBox field(int x, int y, int w, String hint, String value, int limit) {
        EditBox box = new EditBox(font, x, y, w, 20, Component.literal(hint));
        box.setHint(Component.literal(hint));
        box.setMaxLength(limit);
        box.setValue(value == null ? "" : value);
        addRenderableWidget(box);
        return box;
    }

    private void save() {
        try {
            AiAssistantConfig config = new AiAssistantConfig();
            config.endpoint = endpoint.getValue().trim();
            config.model = model.getValue().trim();
            config.apiKey = apiKey.getValue().trim();
            config.save();
            status = "Настройки сохранены на этом компьютере.";
        } catch (Exception e) {
            status = "Не удалось сохранить настройки.";
        }
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        g.drawCenteredString(font, "Настройка ИИ-помощника", width / 2, Math.max(18, height / 2 - 126), 0xFFFFFFFF);
        g.drawString(font, "Endpoint для API, совместимого с Chat Completions", (width - Math.min(440, width - 32)) / 2,
                Math.max(34, height / 2 - 78), 0xFFB5C4D6);
        g.drawString(font, "Название модели, например указанное вашим API-провайдером", (width - Math.min(440, width - 32)) / 2,
                Math.max(34, height / 2 - 30), 0xFFB5C4D6);
        g.drawString(font, "API-ключ хранится открытым текстом в локальном config-файле.",
                (width - Math.min(440, width - 32)) / 2, Math.max(34, height / 2 + 18), 0xFFFFD080);
        if (!status.isBlank()) g.drawCenteredString(font, status, width / 2, Math.min(height - 20, height / 2 + 94), 0xFF9CCC8A);
        super.render(g, mouseX, mouseY, delta);
    }
}
