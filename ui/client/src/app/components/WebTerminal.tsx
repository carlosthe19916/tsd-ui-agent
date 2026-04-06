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

  return <div ref={containerRef} style={{ height: "35vh", width: "100%" }} />;
};
