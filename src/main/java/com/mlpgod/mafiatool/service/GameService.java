package com.mlpgod.mafiatool.service;

import com.mlpgod.mafiatool.model.*;
import com.mlpgod.mafiatool.storage.GameRepository;
import com.mlpgod.mafiatool.storage.RoleRepository;

import java.security.SecureRandom;
import java.util.*;

/** All game mutations live here; screens only call these commands and read snapshots. */
public final class GameService {
    private final GameRepository games;
    private final RoleRepository roles;
    private final SecureRandom random = new SecureRandom();
    private List<GameRecord> cache;
    private List<RoleDefinition> roleCache;

    private static final class Holder {
        private static final GameService INSTANCE = new GameService(new GameRepository(), new RoleRepository());
    }

    public static GameService get() { return Holder.INSTANCE; }

    public GameService(GameRepository games, RoleRepository roles) {
        this.games = games;
        this.roles = roles;
    }

    public List<GameRecord> allGames() {
        if (cache == null) cache = games.loadAll();
        return List.copyOf(cache);
    }

    public List<GameRecord> activeGames() {
        return allGames().stream().filter(g -> g.status != GameStatus.FINISHED).toList();
    }

    public List<GameRecord> archivedGames() {
        return allGames().stream().filter(g -> g.status == GameStatus.FINISHED).toList();
    }

    public List<String> recoveryMessages() {
        allGames();
        return games.recoveryMessages();
    }

    public List<RoleDefinition> roleTemplates() {
        if (roleCache == null) roleCache = roles.load();
        return roleCache;
    }

    public void saveRoleTemplates() { roles.save(roleTemplates()); }

    public void setRoleTemplateEnabled(RoleDefinition role, boolean enabled) {
        RoleDefinition stored = roleTemplates().stream().filter(r -> r.id.equals(role.id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Шаблон роли не найден"));
        stored.enabled = enabled;
        saveRoleTemplates();
    }

    public GameRecord find(String id) {
        return allGames().stream().filter(g -> g.id.equals(id)).findFirst().orElse(null);
    }

    public GameRecord createGame(String title) {
        GameRecord game = new GameRecord();
        game.title = title == null || title.isBlank() ? "Новая партия" : title.trim();
        for (RoleDefinition role : roleTemplates()) game.rolePlan.add(new RoleSelection(role));
        addEvent(game, EventType.GAME_CREATED, null, null, "Ведущий создал партию", "", "");
        store(game);
        return game;
    }

    public void renameGame(GameRecord game, String title) {
        requireEditable(game);
        if (title == null || title.isBlank()) throw new IllegalArgumentException("Название не может быть пустым");
        game.title = title.trim();
        store(game);
    }

    public void addPlayer(GameRecord game, String name) {
        requirePreparing(game);
        requireRolesNotAssigned(game);
        String value = validateName(name);
        if (game.players.stream().anyMatch(p -> p.name.equalsIgnoreCase(value)))
            throw new IllegalArgumentException("Игрок с таким ником уже добавлен");
        game.players.add(new GamePlayer(value));
        store(game);
    }

    public void addPlayers(GameRecord game, Collection<String> names) {
        requirePreparing(game);
        requireRolesNotAssigned(game);
        Set<String> combined = new HashSet<>();
        for (GamePlayer player : game.players) combined.add(player.name.toLowerCase(Locale.ROOT));
        List<GamePlayer> added = new ArrayList<>();
        for (String name : names) {
            String value = validateName(name);
            if (!combined.add(value.toLowerCase(Locale.ROOT)))
                throw new IllegalArgumentException("Повторяется ник: " + value);
            added.add(new GamePlayer(value));
        }
        game.players.addAll(added);
        store(game);
    }

    public void renamePlayer(GameRecord game, String playerId, String newName) {
        requireEditable(game);
        GamePlayer player = player(game, playerId);
        String value = validateName(newName);
        if (game.players.stream().anyMatch(p -> !p.id.equals(playerId) && p.name.equalsIgnoreCase(value)))
            throw new IllegalArgumentException("Такой ник уже есть в партии");
        String oldName = player.name;
        player.name = value;
        addEvent(game, EventType.PLAYER_RENAMED, player.id, player.id, oldName + " → " + value, "", "");
        store(game);
    }

    public void removePreparingPlayer(GameRecord game, String playerId) {
        requirePreparing(game);
        requireRolesNotAssigned(game);
        GamePlayer player = player(game, playerId);
        game.players.remove(player);
        store(game);
    }

    public void setRole(RoleDefinition role, String name, RoleTeam team, String customTeam,
                        String description, RolePhase phase, int count, boolean enabled) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Введите название роли");
        if (count < 0 || count > 50) throw new IllegalArgumentException("Количество должно быть от 0 до 50");
        if (roleCache == null) roleCache = roles.load();
        if (roleCache.stream().anyMatch(r -> r != role && r.name.equalsIgnoreCase(name.trim())))
            throw new IllegalArgumentException("Роль с таким названием уже существует");
        role.name = name.trim();
        role.team = Objects.requireNonNull(team);
        role.customTeam = customTeam == null ? "" : customTeam.trim();
        role.description = description == null ? "" : description.trim();
        role.actionPhase = Objects.requireNonNull(phase);
        role.count = count;
        role.enabled = enabled;
        saveRoleTemplates();
    }

    public RoleDefinition addRole() {
        RoleDefinition role = new RoleDefinition();
        roleCache = roleTemplates();
        roleCache.add(role);
        saveRoleTemplates();
        return role;
    }

    public void deleteRole(RoleDefinition role) {
        roleTemplates().removeIf(r -> r.id.equals(role.id));
        saveRoleTemplates();
    }

    public int enabledRoleCount() {
        return roleTemplates().stream().filter(r -> r.enabled).mapToInt(r -> r.count).sum();
    }

    public int enabledRoleCount(GameRecord game) {
        return game.rolePlan == null ? 0 : game.rolePlan.stream().filter(r -> r.enabled).mapToInt(r -> r.count).sum();
    }

    public void addMissingRoleTemplates(GameRecord game) {
        requirePreparing(game);
        if (game.rolesAssigned) throw new IllegalStateException("Раздача уже зафиксирована");
        if (game.rolePlan == null) game.rolePlan = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (RoleSelection selection : game.rolePlan) if (selection != null) ids.add(selection.templateId);
        for (RoleDefinition template : roleTemplates()) if (!ids.contains(template.id)) game.rolePlan.add(new RoleSelection(template));
        store(game);
    }

    public void setGameRoleSelection(GameRecord game, String templateId, boolean enabled, int count) {
        requirePreparing(game);
        requireRolesNotAssigned(game);
        if (count < 0 || count > 50) throw new IllegalArgumentException("Количество должно быть от 0 до 50");
        if (game.rolePlan == null) throw new IllegalStateException("У партии нет списка ролей");
        RoleSelection selection = game.rolePlan.stream().filter(r -> r.templateId.equals(templateId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Шаблон роли не найден"));
        if (enabled && count < 1) throw new IllegalArgumentException("Включённой роли нужно назначить минимум одного игрока");
        selection.enabled = enabled;
        selection.count = count;
        store(game);
    }

    public List<String> validationErrors(GameRecord game) {
        List<String> errors = new ArrayList<>();
        if (game.players.isEmpty()) errors.add("Добавьте хотя бы одного игрока");
        Set<String> names = new HashSet<>();
        for (GamePlayer p : game.players) if (!names.add(p.name.toLowerCase(Locale.ROOT))) errors.add("Ники участников повторяются");
        int selected = enabledRoleCount(game);
        if (selected != game.players.size())
            errors.add("Количество выбранных ролей (" + selected + ") должно равняться игрокам (" + game.players.size() + ")");
        if (game.rolePlan == null) errors.add("У партии не сохранён список ролей");
        for (RoleSelection selection : game.rolePlan == null ? List.<RoleSelection>of() : game.rolePlan) {
            if (selection.enabled && (selection.count < 1 || selection.role == null))
                errors.add("У выбранной роли некорректное количество или отсутствует шаблон");
        }
        return errors;
    }

    /** Assigns roles exactly once. Viewing/reopening screens never calls this command. */
    public void distributeRoles(GameRecord game) {
        performDistribution(game, false);
    }

    private void performDistribution(GameRecord game, boolean replacement) {
        requirePreparing(game);
        if (game.rolesAssigned) throw new IllegalStateException("Роли уже распределены. Для перераспределения используйте отдельную команду с подтверждением");
        List<String> errors = validationErrors(game);
        if (!errors.isEmpty()) throw new IllegalStateException(String.join("\n", errors));
        List<RoleDefinition> pool = new ArrayList<>();
        for (RoleSelection selection : game.rolePlan) if (selection.enabled)
            for (int i = 0; i < selection.count; i++) pool.add(selection);
        for (int i = pool.size() - 1; i > 0; i--) Collections.swap(pool, i, random.nextInt(i + 1));
        for (int i = 0; i < game.players.size(); i++) game.players.get(i).role = copy(pool.get(i).role);
        game.rolesAssigned = true;
        addEvent(game, replacement ? EventType.ROLES_REDISTRIBUTED : EventType.ROLES_ASSIGNED,
                null, null, replacement ? "Ведущий перераспределил роли" : "Ведущий распределил роли", "Роли зафиксированы", "");
        store(game);
    }

    public void redistributeRoles(GameRecord game) {
        requirePreparing(game);
        List<String> errors = validationErrors(game);
        if (!errors.isEmpty()) throw new IllegalStateException(String.join("\n", errors));
        game.rolesAssigned = false;
        performDistribution(game, true);
    }

    public void startGame(GameRecord game, PhaseKind initialPhase) {
        requirePreparing(game);
        if (!game.rolesAssigned || game.players.stream().anyMatch(p -> p.role == null))
            throw new IllegalStateException("Сначала распределите роли");
        if (initialPhase != PhaseKind.DAY && initialPhase != PhaseKind.NIGHT)
            throw new IllegalArgumentException("Начальная фаза должна быть днём или ночью");
        game.status = GameStatus.ACTIVE;
        game.startedAt = System.currentTimeMillis();
        game.phase = new GamePhase(initialPhase, 1);
        addEvent(game, EventType.GAME_STARTED, null, null, "Партия началась", game.phase.toString(), "");
        store(game);
    }

    public void advancePhase(GameRecord game) {
        requireActive(game);
        GamePhase previous = copy(game.phase);
        if (game.phase.kind == PhaseKind.NIGHT) game.phase = new GamePhase(PhaseKind.DAY, game.phase.number);
        else if (game.phase.kind == PhaseKind.DAY) game.phase = new GamePhase(PhaseKind.NIGHT, game.phase.number + 1);
        else throw new IllegalStateException("Текущую фазу нельзя переключить");
        addEvent(game, EventType.PHASE_CHANGED, null, null, previous.toString() + " → " + game.phase, "", "");
        store(game);
    }

    public void correctPhase(GameRecord game, PhaseKind kind, int number, String reason) {
        requireActive(game);
        if ((kind != PhaseKind.DAY && kind != PhaseKind.NIGHT) || number < 1)
            throw new IllegalArgumentException("Укажите день или ночь и номер от 1");
        GamePhase previous = copy(game.phase);
        game.phase = new GamePhase(kind, number);
        String originalId = game.events.stream().filter(e -> e.type == EventType.PHASE_CHANGED)
                .reduce((a, b) -> b).map(e -> e.id).orElse(null);
        addCorrection(game, originalId, "Исправлена фаза " + previous + " → " + game.phase, reason);
        store(game);
    }

    public void recordAction(GameRecord game, EventType type, String actorId, String targetId,
                             String description, String result) {
        requireActive(game);
        if (type != EventType.ACTION && type != EventType.KILL_ATTEMPT && type != EventType.CHECK
                && type != EventType.TREATMENT && type != EventType.CUSTOM_ACTION)
            throw new IllegalArgumentException("Для этого типа используйте специальную команду");
        GamePlayer actor = actorId == null ? null : activePlayer(game, actorId);
        GamePlayer target = targetId == null ? null : activePlayer(game, targetId);
        addEvent(game, type, actor == null ? null : actor.id, target == null ? null : target.id,
                actor == null ? "Ведущий" : actor.name, target == null ? "" : target.name, description, result);
        store(game);
    }

    public void setPlayerStatus(GameRecord game, String playerId, PlayerStatus status, String reason) {
        requireEditable(game);
        GamePlayer player = player(game, playerId);
        PlayerStatus old = player.status;
        if (old == status) return;
        player.status = status;
        EventType type = switch (status) {
            case DEAD -> EventType.PLAYER_DIED;
            case EXCLUDED -> EventType.PLAYER_EXCLUDED;
            case ALIVE -> old == PlayerStatus.DEAD ? EventType.PLAYER_REVIVED : EventType.PLAYER_RESTORED;
        };
        addEvent(game, type, null, player.id, "Ведущий", player.name,
                status == PlayerStatus.DEAD ? "Игрок отмечен погибшим" : status == PlayerStatus.EXCLUDED ? "Игрок исключён из партии" : "Игрок возвращён в партию",
                reason);
        store(game);
    }

    public void changePlayerRole(GameRecord game, String playerId, RoleDefinition replacement, String reason) {
        requireEditable(game);
        GamePlayer player = player(game, playerId);
        String previous = player.role == null ? "Без роли" : player.role.name;
        player.role = new RoleSnapshot(replacement);
        addEvent(game, EventType.ROLE_CHANGED, null, player.id, "Ведущий", player.name,
                previous + " → " + replacement.name, reason);
        store(game);
    }

    public void castVote(GameRecord game, String voterId, String targetId) {
        requireActive(game);
        if (game.phase.kind != PhaseKind.DAY) throw new IllegalStateException("Голосовать можно только днём");
        GamePlayer voter = activePlayer(game, voterId);
        GamePlayer target = targetId == null || targetId.isBlank() ? null : activePlayer(game, targetId);
        VoteRecord vote = new VoteRecord();
        vote.voterId = voter.id;
        vote.targetId = target == null ? null : target.id;
        vote.dayNumber = game.phase.number;
        game.votes.add(vote);
        addEvent(game, EventType.VOTE, voter.id, target == null ? null : target.id, voter.name,
                target == null ? "Воздержался" : target.name, "Голос", "День " + game.phase.number);
        store(game);
    }

    public Map<String, Integer> voteTally(GameRecord game, int dayNumber) {
        Map<String, VoteRecord> latest = new LinkedHashMap<>();
        game.votes.stream().filter(v -> v.dayNumber == dayNumber).sorted(Comparator.comparingLong(v -> v.timestamp))
                .forEach(v -> latest.put(v.voterId, v));
        Map<String, Integer> tally = new LinkedHashMap<>();
        for (VoteRecord vote : latest.values()) if (vote.targetId != null)
            tally.merge(vote.targetId, 1, Integer::sum);
        return Map.copyOf(tally);
    }

    public List<String> voteLeaders(GameRecord game, int dayNumber) {
        Map<String, Integer> tally = voteTally(game, dayNumber);
        int max = tally.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        if (max == 0) return List.of();
        return tally.entrySet().stream().filter(e -> e.getValue() == max).map(Map.Entry::getKey).toList();
    }

    public void recordVoteResult(GameRecord game, String result, String note) {
        requireActive(game);
        if (game.phase.kind != PhaseKind.DAY) throw new IllegalStateException("Итог голосования фиксируется днём");
        addEvent(game, EventType.VOTE_RESULT, null, null, "Ведущий", "", result, note);
        store(game);
    }

    public void correctEvent(GameRecord game, String originalId, String correctedResult, String reason) {
        requireEditable(game);
        if (game.events.stream().noneMatch(e -> e.id.equals(originalId))) throw new IllegalArgumentException("Событие не найдено");
        addCorrection(game, originalId, correctedResult, reason);
        store(game);
    }

    public List<GameEvent> playerHistory(GameRecord game, String playerId) {
        Set<String> related = new HashSet<>();
        for (GameEvent event : game.events) {
            if (Objects.equals(event.actorId, playerId) || Objects.equals(event.targetId, playerId)) related.add(event.id);
        }
        return game.events.stream().filter(e -> Objects.equals(e.actorId, playerId)
                || Objects.equals(e.targetId, playerId) || related.contains(e.originalEventId)).toList();
    }

    public List<GameEvent> filterEvents(GameRecord game, PhaseKind phase, String playerId, EventType type) {
        Set<String> related = new HashSet<>();
        if (playerId != null) for (GameEvent event : game.events) {
            if (Objects.equals(event.actorId, playerId) || Objects.equals(event.targetId, playerId)) related.add(event.id);
        }
        return game.events.stream()
                .filter(e -> phase == null || e.phase.kind == phase)
                .filter(e -> playerId == null || Objects.equals(e.actorId, playerId)
                        || Objects.equals(e.targetId, playerId) || related.contains(e.originalEventId))
                .filter(e -> type == null || e.type == type).toList();
    }

    public void finishGame(GameRecord game, String winningTeam, String comment) {
        requireActive(game);
        game.status = GameStatus.FINISHED;
        game.finishedAt = System.currentTimeMillis();
        game.phase = new GamePhase(PhaseKind.FINISHED, game.phase.number);
        game.winningTeam = winningTeam == null ? "" : winningTeam.trim();
        game.hostComment = comment == null ? "" : comment.trim();
        addEvent(game, EventType.GAME_FINISHED, null, null, "Ведущий завершил партию", game.winningTeam, game.hostComment, "");
        store(game);
    }

    public void reopenGame(GameRecord game) {
        if (game.status != GameStatus.FINISHED) throw new IllegalStateException("Партия не завершена");
        game.status = GameStatus.ACTIVE;
        game.phase = lastPlayPhase(game);
        game.finishedAt = 0;
        addEvent(game, EventType.GAME_REOPENED, null, null, "Архивная партия восстановлена", game.phase.toString(), "", "");
        store(game);
    }

    public boolean deleteGame(GameRecord game) {
        boolean deleted = games.delete(game.id);
        if (cache != null) cache.removeIf(g -> g.id.equals(game.id));
        return deleted;
    }

    public GameRecord importGame(String json) {
        GameRecord game = games.importJson(json);
        if (cache == null) cache = new ArrayList<>(games.loadAll());
        cache.add(game);
        store(game);
        return game;
    }

    public String exportGame(GameRecord game) { return games.exportJson(game); }

    private GamePhase lastPlayPhase(GameRecord game) {
        return game.events.stream().filter(e -> e.phase.kind == PhaseKind.DAY || e.phase.kind == PhaseKind.NIGHT)
                .reduce((a, b) -> b).map(e -> copy(e.phase)).orElse(new GamePhase(PhaseKind.NIGHT, 1));
    }

    private GamePlayer player(GameRecord game, String id) {
        return game.players.stream().filter(p -> p.id.equals(id)).findFirst().orElseThrow(() -> new IllegalArgumentException("Игрок не найден"));
    }

    private GamePlayer activePlayer(GameRecord game, String id) {
        GamePlayer player = player(game, id);
        if (player.status != PlayerStatus.ALIVE) throw new IllegalStateException("Мёртвый или исключённый игрок не участвует в новых действиях");
        return player;
    }

    private void addEvent(GameRecord game, EventType type, String actorId, String targetId,
                          String actor, String target, String description, String result) {
        game.events.add(new GameEvent(game.phase, type, actorId, targetId, actor, target, description, result));
    }

    private void addCorrection(GameRecord game, String originalId, String result, String reason) {
        GameEvent correction = new GameEvent(game.phase, EventType.CORRECTION, null, null,
                "Ведущий", "", "Исправление записи", result);
        correction.originalEventId = originalId;
        correction.correctionNote = reason == null ? "" : reason;
        game.events.add(correction);
    }

    private void store(GameRecord game) {
        game.updatedAt = System.currentTimeMillis();
        if (cache == null) cache = new ArrayList<>(games.loadAll());
        cache.removeIf(g -> g.id.equals(game.id));
        cache.add(game);
        cache.sort(Comparator.comparingLong((GameRecord g) -> g.updatedAt).reversed());
        games.save(game);
    }

    private String validateName(String name) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Ник не может быть пустым");
        String value = name.trim();
        if (value.length() > 32) throw new IllegalArgumentException("Максимальная длина ника — 32 символа");
        return value;
    }

    private void requirePreparing(GameRecord game) {
        if (game.status != GameStatus.PREPARING) throw new IllegalStateException("Изменять состав можно только до начала партии");
    }

    private void requireRolesNotAssigned(GameRecord game) {
        if (game.rolesAssigned) throw new IllegalStateException("Раздача уже закреплена. Сначала выполните перераспределение с подтверждением");
    }

    private void requireActive(GameRecord game) {
        if (game.status != GameStatus.ACTIVE) throw new IllegalStateException("Партия сейчас не активна");
    }

    private void requireEditable(GameRecord game) {
        if (game.status == GameStatus.FINISHED) throw new IllegalStateException("Архив доступен только для просмотра");
    }

    private GamePhase copy(GamePhase phase) { return new GamePhase(phase.kind, phase.number); }
    private RoleSnapshot copy(RoleSnapshot role) {
        RoleSnapshot copy = new RoleSnapshot();
        copy.templateId = role.templateId; copy.name = role.name; copy.team = role.team;
        copy.customTeam = role.customTeam; copy.description = role.description; copy.actionPhase = role.actionPhase;
        return copy;
    }
}
