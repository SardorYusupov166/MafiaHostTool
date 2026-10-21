package com.mlpgod.mafiatool.model;

public final class RoleSnapshot {
    public String templateId;
    public String name;
    public RoleTeam team;
    public String customTeam;
    public String description;
    public RolePhase actionPhase;

    public RoleSnapshot() {}

    public RoleSnapshot(RoleDefinition role) {
        templateId = role.id;
        name = role.name;
        team = role.team;
        customTeam = role.customTeam;
        description = role.description;
        actionPhase = role.actionPhase;
    }
}
