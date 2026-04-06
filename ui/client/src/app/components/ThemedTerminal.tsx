import React from "react";

import { Terminal } from "@xterm/xterm";
import { FitAddon } from "@xterm/addon-fit";
import "@xterm/xterm/css/xterm.css";

import {
  encodeTtydInput,
  encodeTtydResize,
  decodeTtydMessage,
  isOutputMessage,
} from "@app/utils/ttyd-protocol";

const SOH = "\x01";

const DARK_THEME = {
  background: "#1e1e1e",
  foreground: "#cccccc",
  cursor: "#aeafad",
  selectionBackground: "#264f78",
  black: "#000000",
  red: "#cd3131",
  green: "#0dbc79",
  yellow: "#e5e510",
  blue: "#2472c8",
  magenta: "#bc3fbc",
  cyan: "#11a8cd",
  white: "#a0a0a0",
  brightBlack: "#666666",
  brightRed: "#f14c4c",
  brightGreen: "#23d18b",
  brightYellow: "#f5f543",
  brightBlue: "#3b8eea",
  brightMagenta: "#d670d6",
  brightCyan: "#29b8db",
  brightWhite: "#ffffff",
};

interface ThemedTerminalProps {
  workspaceEntityId: number;
  height?: string;
}

export const ThemedTerminal: React.FC<ThemedTerminalProps> = ({
  workspaceEntityId,
  height = "35vh",
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
      fontFamily: "'Cascadia Code', 'Fira Code', 'Consolas', monospace",
      theme: DARK_THEME,
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
    ws.binaryType = "arraybuffer";

    ws.onopen = () => {
      ws.send(encodeTtydResize(terminal.cols, terminal.rows));
    };

    ws.onmessage = (event) => {
      if (typeof event.data === "string") {
        const data = event.data;
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
        }
      } else if (event.data instanceof ArrayBuffer) {
        const msg = decodeTtydMessage(event.data);
        if (isOutputMessage(msg)) {
          terminal.write(msg.payload);
        }
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
        ws.send(encodeTtydInput(data));
      }
    });

    terminal.onResize(({ cols, rows }) => {
      if (ws.readyState === WebSocket.OPEN) {
        ws.send(encodeTtydResize(cols, rows));
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

  return (
    <div
      style={{
        height,
        width: "100%",
        borderRadius: 4,
        overflow: "hidden",
        border: "1px solid #333",
        background: DARK_THEME.background,
      }}
    >
      <div ref={containerRef} style={{ height: "100%", width: "100%" }} />
    </div>
  );
};
