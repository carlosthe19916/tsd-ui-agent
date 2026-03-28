import React from "react";

import { Terminal } from "@xterm/xterm";
import { FitAddon } from "@xterm/addon-fit";
import "@xterm/xterm/css/xterm.css";

const SOH = "\x01";

interface WebTerminalProps {
  workspaceEntityId: number;
}

export const WebTerminal: React.FC<WebTerminalProps> = ({
  workspaceEntityId,
}) => {
  const containerRef = React.useRef<HTMLDivElement>(null);
  const initializedRef = React.useRef(false);
  const terminalRef = React.useRef<Terminal | null>(null);
  const wsRef = React.useRef<WebSocket | null>(null);
  const fitAddonRef = React.useRef<FitAddon | null>(null);

  React.useEffect(() => {
    const container = containerRef.current;
    if (!container || initializedRef.current) return;
    initializedRef.current = true;

    const terminal = new Terminal({
      cursorBlink: true,
      fontSize: 14,
      fontFamily: "monospace",
      theme: {
        background: "#1e1e1e",
      },
    });
    terminalRef.current = terminal;

    const fitAddon = new FitAddon();
    fitAddonRef.current = fitAddon;
    terminal.loadAddon(fitAddon);
    terminal.open(container);
    fitAddon.fit();

    const protocol = location.protocol === "https:" ? "wss:" : "ws:";
    const ws = new WebSocket(
      `${protocol}//${location.host}/ws/terminal/${workspaceEntityId}`,
    );
    wsRef.current = ws;

    ws.onopen = () => {
      ws.send(
        SOH +
          JSON.stringify({
            type: "resize",
            cols: terminal.cols,
            rows: terminal.rows,
          }),
      );
    };

    ws.onmessage = (event) => {
      const data = event.data as string;
      if (data.startsWith(SOH)) {
        try {
          const msg = JSON.parse(data.substring(1));
          if (msg.type === "error") {
            terminal.writeln(`\r\n\x1b[31mError: ${msg.message}\x1b[0m`);
          } else if (msg.type === "exit") {
            terminal.writeln(
              `\r\n\x1b[33mProcess exited with code ${msg.code}\x1b[0m`,
            );
          }
        } catch {
          // Ignore parse errors
        }
      } else {
        terminal.write(data);
      }
    };

    ws.onclose = () => {
      terminal.writeln("\r\n\x1b[33mConnection closed.\x1b[0m");
    };

    ws.onerror = () => {
      terminal.writeln("\r\n\x1b[31mWebSocket error.\x1b[0m");
    };

    terminal.onData((data) => {
      if (ws.readyState === WebSocket.OPEN) {
        ws.send(data);
      }
    });

    terminal.onResize(({ cols, rows }) => {
      if (ws.readyState === WebSocket.OPEN) {
        ws.send(SOH + JSON.stringify({ type: "resize", cols, rows }));
      }
    });

    const resizeObserver = new ResizeObserver(() => {
      fitAddon.fit();
    });
    resizeObserver.observe(container);

    return () => {
      resizeObserver.disconnect();
      ws.close();
      terminal.dispose();
      terminalRef.current = null;
      wsRef.current = null;
      fitAddonRef.current = null;
      initializedRef.current = false;
    };
  }, [workspaceEntityId]);

  return <div ref={containerRef} style={{ height: "35vh", width: "100%" }} />;
};
