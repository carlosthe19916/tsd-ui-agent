import React from "react";

import { Banner, Spinner } from "@patternfly/react-core";
import { LogViewer } from "@patternfly/react-log-viewer";

import { streamPlanOutput } from "@app/api/task-api";
import { ThemeContext } from "@app/components/ThemeContext";

interface ExecutionOutputPanelProps {
  taskId: number;
  isActive: boolean;
}

export const ExecutionOutputPanel: React.FC<ExecutionOutputPanelProps> = ({
  taskId,
  isActive,
}) => {
  const { isDark } = React.useContext(ThemeContext);
  const [logLines, setLogLines] = React.useState<string[]>([]);
  const [isStreaming, setIsStreaming] = React.useState(false);
  const linesRef = React.useRef<string[]>([]);
  const rafRef = React.useRef<number>();

  React.useEffect(() => {
    if (!isActive) return;

    const abortController = new AbortController();
    setIsStreaming(true);
    setLogLines([]);
    linesRef.current = [];

    (async () => {
      try {
        for await (const line of streamPlanOutput(
          taskId,
          abortController.signal,
        )) {
          linesRef.current = [...linesRef.current, line];
          if (rafRef.current == null) {
            rafRef.current = requestAnimationFrame(() => {
              rafRef.current = undefined;
              setLogLines([...linesRef.current]);
            });
          }
        }
      } catch (e) {
        if (e instanceof DOMException && e.name === "AbortError") return;
      } finally {
        setLogLines([...linesRef.current]);
        setIsStreaming(false);
      }
    })();

    return () => {
      abortController.abort();
      if (rafRef.current != null) {
        cancelAnimationFrame(rafRef.current);
        rafRef.current = undefined;
      }
    };
  }, [taskId, isActive]);

  const data = logLines.length > 0 ? logLines.join("\n") : " ";

  return (
    <div style={{ marginTop: "var(--pf-t--global--spacer--md)" }}>
      <LogViewer
        data={data}
        hasLineNumbers
        height={400}
        isTextWrapped={false}
        scrollToRow={logLines.length}
        theme={isDark ? "dark" : "light"}
        header={
          <Banner variant={isStreaming ? "blue" : "green"}>
            {isStreaming ? (
              <>
                <Spinner size="sm" /> Streaming output... ({logLines.length}{" "}
                lines)
              </>
            ) : (
              <>Completed ({logLines.length} lines)</>
            )}
          </Banner>
        }
      />
    </div>
  );
};
