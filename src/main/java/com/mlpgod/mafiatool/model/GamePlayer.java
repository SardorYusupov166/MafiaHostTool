package com.mlpgod.mafiatool.model;

import java.util.UUID;

public final class GamePlayer {
    public String id = UUID.randomUUID().toString();
    public String name;
    public PlayerStatus status = PlayerStatus.ALIVE;
    public RoleSnapshot role;

    public GamePlayer() {}

    public GamePlayer(String name) {
        this.name = name;
    }
}
