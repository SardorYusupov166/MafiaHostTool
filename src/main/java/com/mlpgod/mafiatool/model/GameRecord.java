package com.mlpgod.mafiatool.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class GameRecord {
    public int schemaVersion = 1;
    public String id = UUID.randomUUID().toString();
    public String title = "Новая партия";
    public GameStatus status = GameStatus.PREPARING;
    public boolean rolesAssigned;
    public GamePhase phase = new GamePhase();
    public long createdAt = System.currentTimeMillis();
    public long updatedAt = createdAt;
    public long startedAt;
    public long finishedAt;
    public String winningTeam = "";
    public String hostComment = "";
    public List<GamePlayer> players = new ArrayList<>();
    public List<RoleSelection> rolePlan = new ArrayList<>();
    public List<GameEvent> events = new ArrayList<>();
    public List<VoteRecord> votes = new ArrayList<>();

    public int aliveCount() {
        return (int) players.stream().filter(p -> p.status == PlayerStatus.ALIVE).count();
    }

    public int activeCount() {
        return (int) players.stream().filter(p -> p.status == PlayerStatus.ALIVE).count();
    }
}
