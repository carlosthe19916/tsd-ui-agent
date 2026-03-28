package org.acme.services.terminal;

import com.pty4j.PtyProcess;

public record TerminalSession(String id, PtyProcess process) {
}
