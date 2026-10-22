package com.mlpgod.mafiatool.model;

import java.util.UUID;

/** Reusable role template. A game stores a separate snapshot of it per player. */
public final class RoleDefinition {
    public String id = UUID.randomUUID().toString();
    public String name = "Новая роль";
    public RoleTeam team = RoleTeam.TOWN;
    public RoleColor color;
    public String customTeam = "";
    public String description = "";
    public RolePhase actionPhase = RolePhase.ANY;
    public int count = 1;
    public boolean enabled;

    public RoleDefinition() {}

    public RoleDefinition(String name, RoleTeam team, int count, boolean enabled) {
        this.name = name;
        this.team = team;
        this.color = RoleColor.defaultFor(team);
        this.count = count;
        this.enabled = enabled;
    }
}
