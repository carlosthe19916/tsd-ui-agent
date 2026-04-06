package org.acme.services.terminal;

public record TerminalSession(String id, Process ttydProcess, int port) {
}
