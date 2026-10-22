package com.mlpgod.mafiatool.service;

import com.mlpgod.mafiatool.model.*;
import com.mlpgod.mafiatool.storage.GameRepository;
import com.mlpgod.mafiatool.storage.RoleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GameServiceTest {
    @TempDir Path temp;

    private GameService service(Path root) {
        return new GameService(new GameRepository(root), new RoleRepository(root));
    }

    private GameRecord preparedFourPlayerGame(GameService service) {
        GameRecord game = service.createGame("Test");
        service.addPlayers(game, List.of("A", "B", "C", "D"));
        assertTrue(service.validationErrors(game).isEmpty());
        service.distributeRoles(game);
        assertTrue(game.players.stream().allMatch(p -> p.role != null));
        return game;
    }

    @Test
    void roleDistributionIsExactAndIsNotRepeatedByReloading() {
        GameService firstRun = service(temp);
        GameRecord game = preparedFourPlayerGame(firstRun);
        assertEquals(4, game.players.size());
        assertEquals(4, game.players.stream().map(p -> p.role.name).count());
        assertThrows(IllegalStateException.class, () -> firstRun.distributeRoles(game));

        GameService afterRestart = service(temp);
        GameRecord loaded = afterRestart.find(game.id);
        assertNotNull(loaded);
        assertTrue(loaded.rolesAssigned);
        assertEquals(game.players.stream().map(p -> p.role.name).sorted().toList(),
                loaded.players.stream().map(p -> p.role.name).sorted().toList());
    }

    @Test
    void missingActionRolesAreAutomaticallyFilledWithPeacefulTownMembers() {
        GameService service = service(temp);
        GameRecord game = service.createGame("Ten players, six active roles");
        service.addPlayers(game, List.of("A", "B", "C", "D", "E", "F", "G", "H", "I", "J"));
        RoleSelection mafia = game.rolePlan.stream()
                .filter(selection -> selection.role.name.equals("Мафия")).findFirst().orElseThrow();
        service.setGameRoleSelection(game, mafia.templateId, true, 4);

        assertEquals(6, service.enabledRoleCount(game));
        assertTrue(service.validationErrors(game).isEmpty());
        service.distributeRoles(game);

        assertEquals(10, game.players.size());
        assertEquals(6, game.players.stream().filter(player -> !player.role.name.equals("Мирный житель")).count());
        assertEquals(4, game.players.stream().filter(player -> player.role.name.equals("Мирный житель")).count());
        assertTrue(game.players.stream().filter(player -> player.role.name.equals("Мирный житель"))
                .allMatch(player -> player.role.team == RoleTeam.TOWN));
    }

    @Test
    void validationRejectsMoreActionRolesThanPlayers() {
        GameService service = service(temp);
        GameRecord game = service.createGame("Too many roles");
        service.addPlayers(game, List.of("A", "B", "C"));
        RoleSelection mafia = game.rolePlan.stream()
                .filter(selection -> selection.role.name.equals("Мафия")).findFirst().orElseThrow();
        service.setGameRoleSelection(game, mafia.templateId, true, 4);

        assertTrue(service.validationErrors(game).stream().anyMatch(error -> error.contains("больше, чем игроков")));
        assertThrows(IllegalStateException.class, () -> service.distributeRoles(game));
    }

    @Test
    void killAttemptDoesNotAutomaticallyKillTargetAndPhasesAdvanceInOrder() {
        GameService service = service(temp);
        GameRecord game = preparedFourPlayerGame(service);
        service.startGame(game, PhaseKind.NIGHT);
        service.recordAction(game, EventType.KILL_ATTEMPT, game.players.get(0).id, game.players.get(1).id,
                "Попытка убийства", "Цель спасена доктором");
        assertEquals(PlayerStatus.ALIVE, game.players.get(1).status);
        service.advancePhase(game);
        assertEquals(new GamePhase(PhaseKind.DAY, 1).toString(), game.phase.toString());
        service.advancePhase(game);
        assertEquals(new GamePhase(PhaseKind.NIGHT, 2).toString(), game.phase.toString());
        assertTrue(game.events.stream().anyMatch(e -> e.type == EventType.KILL_ATTEMPT && e.result.equals("Цель спасена доктором")));
    }

    @Test
    void votingUsesLatestBallotAndReportsTies() {
        GameService service = service(temp);
        GameRecord game = preparedFourPlayerGame(service);
        service.startGame(game, PhaseKind.DAY);
        service.castVote(game, game.players.get(0).id, game.players.get(1).id);
        service.castVote(game, game.players.get(0).id, game.players.get(2).id);
        service.castVote(game, game.players.get(1).id, game.players.get(1).id);
        service.castVote(game, game.players.get(2).id, game.players.get(2).id);
        service.castVote(game, game.players.get(3).id, game.players.get(1).id);
        assertEquals(2, service.voteLeaders(game, 1).size());
        service.setPlayerStatus(game, game.players.get(3).id, PlayerStatus.DEAD, "Проверка статуса");
        assertThrows(IllegalStateException.class, () -> service.castVote(game, game.players.get(3).id, game.players.get(1).id));
    }

    @Test
    void corruptPartyFileDoesNotHideOtherParties() throws Exception {
        GameRepository repository = new GameRepository(temp);
        GameRecord good = new GameRecord();
        good.title = "Сохранная партия";
        repository.save(good);
        GameRecord damaged = new GameRecord();
        damaged.title = "Повреждённая партия";
        repository.save(damaged);
        Path damagedPath = temp.resolve("mafiatool/games").resolve(damaged.id + ".json");
        Files.writeString(damagedPath, "{broken json");

        GameRepository loader = new GameRepository(temp);
        List<GameRecord> loaded = loader.loadAll();
        assertEquals(1, loaded.size());
        assertEquals("Сохранная партия", loaded.get(0).title);
        assertFalse(loader.recoveryMessages().isEmpty());
    }

    @Test
    void incompletePartyJsonIsRepairedBeforeScreensReadIt() throws Exception {
        Path games = temp.resolve("mafiatool/games");
        Files.createDirectories(games);
        Files.writeString(games.resolve("incomplete.json"), """
                {"schemaVersion":1,"id":"incomplete","title":null,"status":null,
                 "players":[null,{"id":null,"name":null,"status":null}],
                 "rolePlan":[null,{"templateId":"missing","role":null}],
                 "events":[null,{"phase":null,"type":null,"description":null}],"votes":[null]}
                """);

        GameRepository repository = new GameRepository(temp);
        List<GameRecord> loaded = repository.loadAll();
        assertEquals(1, loaded.size());
        assertEquals("Игра", loaded.get(0).title);
        assertEquals(1, loaded.get(0).players.size());
        assertEquals("Игрок", loaded.get(0).players.get(0).name);
        assertTrue(repository.recoveryMessages().stream().anyMatch(message -> message.contains("исправлены")));
    }

    @Test
    void duplicatePlayerNamesAreRejectedCaseInsensitively() {
        GameService service = service(temp);
        GameRecord game = service.createGame("Duplicate check");
        service.addPlayer(game, "Player");
        assertThrows(IllegalArgumentException.class, () -> service.addPlayer(game, "player"));
    }

    @Test
    void validationReportsIncompleteRowsInsteadOfCrashing() {
        GameService service = service(temp);
        GameRecord game = service.createGame("Incomplete save");
        game.players.add(null);
        game.rolePlan.add(null);

        List<String> errors = assertDoesNotThrow(() -> service.validationErrors(game));
        assertTrue(errors.stream().anyMatch(error -> error.contains("без ника")));
        assertTrue(errors.stream().anyMatch(error -> error.contains("пустая запись")));
    }

    @Test
    void roleTemplateChangesDoNotRewritePartySnapshot() {
        GameService service = service(temp);
        GameRecord game = service.createGame("Snapshot");
        RoleSelection mafiaInGame = game.rolePlan.stream()
                .filter(selection -> selection.role.name.equals("Мафия")).findFirst().orElseThrow();
        RoleDefinition template = service.roleTemplates().stream()
                .filter(role -> role.id.equals(mafiaInGame.templateId)).findFirst().orElseThrow();

        service.setRole(template, "Банда", template.team, template.customTeam, template.description,
                template.actionPhase, template.count, template.enabled);

        assertEquals("Мафия", mafiaInGame.role.name);
        assertEquals("Банда", service.roleTemplates().stream()
                .filter(role -> role.id.equals(template.id)).findFirst().orElseThrow().name);
    }

    @Test
    void roleColorsDefaultByTeamAndCustomColorsAreCopiedIntoGameRoles() {
        GameService service = service(temp);
        RoleDefinition mafia = service.roleTemplates().stream()
                .filter(role -> role.team == RoleTeam.MAFIA).findFirst().orElseThrow();
        RoleDefinition town = service.roleTemplates().stream()
                .filter(role -> role.team == RoleTeam.TOWN).findFirst().orElseThrow();
        assertEquals(RoleColor.RED, mafia.color);
        assertEquals(RoleColor.GREEN, town.color);

        service.setRole(mafia, mafia.name, mafia.team, mafia.customTeam, RoleColor.PURPLE,
                mafia.description, mafia.actionPhase, mafia.count, mafia.enabled);
        GameRecord game = service.createGame("Colored role snapshot");
        service.addPlayers(game, List.of("A", "B", "C", "D"));
        service.distributeRoles(game);

        assertTrue(game.players.stream().filter(player -> player.role.name.equals(mafia.name))
                .allMatch(player -> player.role.color == RoleColor.PURPLE));
    }

    @Test
    void playerHistoryIncludesCorrectionsToTheirEvents() {
        GameService service = service(temp);
        GameRecord game = preparedFourPlayerGame(service);
        service.startGame(game, PhaseKind.NIGHT);
        GamePlayer target = game.players.get(1);
        service.recordAction(game, EventType.KILL_ATTEMPT, game.players.get(0).id, target.id,
                "Попытка убийства", "Запись ошибочна");
        GameEvent original = game.events.get(game.events.size() - 1);
        service.correctEvent(game, original.id, "Попытки не было", "Уточнение ведущего");

        assertTrue(service.playerHistory(game, target.id).stream()
                .anyMatch(event -> original.id.equals(event.originalEventId)));
    }

    @Test
    void finishedGameCannotBeChangedUntilRestored() {
        GameService service = service(temp);
        GameRecord game = preparedFourPlayerGame(service);
        service.startGame(game, PhaseKind.DAY);
        service.finishGame(game, "Мирные", "Тест архива");

        assertThrows(IllegalStateException.class,
                () -> service.setPlayerStatus(game, game.players.get(0).id, PlayerStatus.DEAD, "После завершения"));
        service.reopenGame(game);
        service.setPlayerStatus(game, game.players.get(0).id, PlayerStatus.DEAD, "После восстановления");
        assertEquals(PlayerStatus.DEAD, game.players.get(0).status);
    }
}
