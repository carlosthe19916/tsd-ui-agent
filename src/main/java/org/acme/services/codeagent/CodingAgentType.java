package org.acme.services.codeagent;

public enum CodingAgentType {
    CLAUDE(".claude"),
    OPENCODE(".opencode");

    public final String configDir;

    CodingAgentType(String configDir) {
        this.configDir = configDir;
    }
}
