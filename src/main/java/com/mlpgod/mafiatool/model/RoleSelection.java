package com.mlpgod.mafiatool.model;

public final class RoleSelection {
    public String templateId;
    public RoleSnapshot role;
    public int count;
    public boolean enabled;

    public RoleSelection() {}

    public RoleSelection(RoleDefinition definition) {
        templateId = definition.id;
        role = new RoleSnapshot(definition);
        count = definition.count;
        enabled = definition.enabled;
    }
}
