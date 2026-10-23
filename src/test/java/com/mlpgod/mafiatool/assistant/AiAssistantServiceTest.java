package com.mlpgod.mafiatool.assistant;

import com.mlpgod.mafiatool.model.GameEvent;
import com.mlpgod.mafiatool.model.GamePhase;
import com.mlpgod.mafiatool.model.GamePlayer;
import com.mlpgod.mafiatool.model.GameRecord;
import com.mlpgod.mafiatool.model.EventType;
import com.mlpgod.mafiatool.model.PhaseKind;
import com.mlpgod.mafiatool.model.RoleSnapshot;
import com.mlpgod.mafiatool.model.RoleDefinition;
import com.mlpgod.mafiatool.model.RoleTeam;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AiAssistantServiceTest {
    @Test
    void contextIncludesRosterAndEventsButNeverRoleNames() {
        GameRecord game = new GameRecord();
        GamePlayer player = new GamePlayer("ИгрокА");
        player.role = new RoleSnapshot(new RoleDefinition("Тайная роль", RoleTeam.MAFIA, 1, false));
        game.players.add(player);
        game.events.add(new GameEvent(new GamePhase(PhaseKind.NIGHT, 1), EventType.ACTION,
                player.id, null, player.name, "", "Проверил игрока", "результат скрыт"));

        String context = AiAssistantService.systemPrompt(game);

        assertTrue(context.contains("ИгрокА"));
        assertTrue(context.contains("Проверил игрока"));
        assertFalse(context.contains("Тайная роль"));
        assertTrue(context.contains("результат скрыт"));
    }

    @Test
    void contextCapsEventsToRecentEighty() {
        GameRecord game = new GameRecord();
        for (int i = 0; i < 82; i++) {
            game.events.add(new GameEvent(new GamePhase(PhaseKind.DAY, 1), EventType.CUSTOM_ACTION,
                    null, null, "Ведущий", "", "Событие " + i, ""));
        }

        String context = AiAssistantService.systemPrompt(game);

        assertFalse(context.contains("Событие 0"));
        assertFalse(context.contains("Событие 1"));
        assertTrue(context.contains("Событие 2"));
        assertTrue(context.contains("Событие 81"));
        assertTrue(context.contains("Показаны только последние 80 событий"));
    }
}
