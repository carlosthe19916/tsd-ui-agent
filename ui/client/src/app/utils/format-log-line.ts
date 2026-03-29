/**
 * Formats a Claude Code stream-json line into a human-readable string.
 * Plain text lines (e.g. from OpenCode) are returned as-is.
 */
export function formatLogLine(raw: string): string[] {
  let parsed: Record<string, unknown>;
  try {
    parsed = JSON.parse(raw);
  } catch {
    // Not JSON (e.g. OpenCode plain text) — return as-is
    return [raw];
  }

  const type = parsed.type as string | undefined;

  if (type === "system") {
    return formatSystem(parsed);
  }

  if (type === "assistant") {
    return formatAssistant(parsed);
  }

  if (type === "user") {
    return formatUser(parsed);
  }

  if (type === "result") {
    return formatResult(parsed);
  }

  // Unknown JSON type — show a summary
  return [`[${type ?? "unknown"}] ${raw.slice(0, 200)}`];
}

function formatSystem(parsed: Record<string, unknown>): string[] {
  const subtype = parsed.subtype as string | undefined;
  if (subtype === "init") {
    const model = parsed.model as string | undefined;
    const cwd = parsed.cwd as string | undefined;
    const lines = [`[system] Session initialized`];
    if (model) lines[0] += ` (model: ${model})`;
    if (cwd) lines.push(`  cwd: ${cwd}`);
    return lines;
  }
  if (subtype === "task_started" || subtype === "task_progress") {
    return [`[system] ${subtype.replace(/_/g, " ")}`];
  }
  return [`[system] ${subtype ?? JSON.stringify(parsed).slice(0, 150)}`];
}

function formatAssistant(parsed: Record<string, unknown>): string[] {
  const message = parsed.message as Record<string, unknown> | undefined;
  if (!message) return ["[assistant]"];

  const contentArr = message.content as
    | Array<Record<string, unknown>>
    | undefined;
  if (!contentArr || contentArr.length === 0) return ["[assistant]"];

  const lines: string[] = [];
  for (const block of contentArr) {
    const blockType = block.type as string;

    if (blockType === "thinking") {
      const thinking = block.thinking as string | undefined;
      if (thinking) {
        const preview =
          thinking.length > 300 ? `${thinking.slice(0, 300)}...` : thinking;
        lines.push(`[thinking] ${preview}`);
      }
      continue;
    }

    if (blockType === "text") {
      const text = block.text as string | undefined;
      if (text) lines.push(text);
      continue;
    }

    if (blockType === "tool_use") {
      const name = block.name as string | undefined;
      const input = block.input as Record<string, unknown> | undefined;
      lines.push(...formatToolUse(name, input));
      continue;
    }

    if (blockType === "tool_result") {
      const content = block.content as unknown;
      if (typeof content === "string") {
        const preview =
          content.length > 500 ? `${content.slice(0, 500)}...` : content;
        lines.push(`[result] ${preview}`);
      } else if (Array.isArray(content)) {
        for (const item of content) {
          if (
            typeof item === "object" &&
            item !== null &&
            (item as Record<string, unknown>).text
          ) {
            const text = (item as Record<string, unknown>).text as string;
            const preview =
              text.length > 500 ? `${text.slice(0, 500)}...` : text;
            lines.push(`[result] ${preview}`);
          }
        }
      }
      continue;
    }

    lines.push(`[${blockType}]`);
  }

  return lines.length > 0 ? lines : ["[assistant]"];
}

function formatUser(parsed: Record<string, unknown>): string[] {
  const message = parsed.message as Record<string, unknown> | undefined;
  if (!message) return ["[user]"];

  const content = message.content as unknown;
  if (typeof content === "string") {
    return [`[user] ${content}`];
  }

  if (Array.isArray(content)) {
    const lines: string[] = [];
    for (const block of content) {
      const b = block as Record<string, unknown>;
      if (b.type === "tool_result") {
        const innerContent = b.content as unknown;
        if (typeof innerContent === "string") {
          const preview =
            innerContent.length > 500
              ? `${innerContent.slice(0, 500)}...`
              : innerContent;
          lines.push(`[tool_result] ${preview}`);
        } else if (Array.isArray(innerContent)) {
          for (const item of innerContent) {
            if (
              typeof item === "object" &&
              item !== null &&
              (item as Record<string, unknown>).text
            ) {
              const text = (item as Record<string, unknown>).text as string;
              const preview =
                text.length > 500 ? `${text.slice(0, 500)}...` : text;
              lines.push(`[tool_result] ${preview}`);
            }
          }
        }
      } else if (b.type === "text") {
        lines.push(`[user] ${b.text}`);
      }
    }
    return lines.length > 0 ? lines : ["[user]"];
  }

  return ["[user]"];
}

function formatResult(parsed: Record<string, unknown>): string[] {
  const result = parsed.result as string | undefined;
  if (result) {
    return [`[result] ${result}`];
  }
  return ["[result]"];
}

function formatToolUse(
  name: string | undefined,
  input: Record<string, unknown> | undefined,
): string[] {
  if (!name) return ["[tool]"];

  const lines: string[] = [];

  switch (name) {
    case "Read":
      lines.push(`[tool] Read ${input?.file_path ?? ""}`);
      break;
    case "Write":
      lines.push(`[tool] Write ${input?.file_path ?? ""}`);
      break;
    case "Edit":
      lines.push(`[tool] Edit ${input?.file_path ?? ""}`);
      break;
    case "Bash":
      lines.push(`[tool] Bash: ${input?.command ?? ""}`);
      break;
    case "Glob":
      lines.push(`[tool] Glob: ${input?.pattern ?? ""}`);
      break;
    case "Grep":
      lines.push(`[tool] Grep: ${input?.pattern ?? ""}`);
      break;
    case "Agent": {
      const desc = input?.description ?? "";
      const agentType = input?.subagent_type ?? "";
      lines.push(`[tool] Agent (${agentType}): ${desc}`);
      break;
    }
    case "Skill":
      lines.push(`[tool] Skill: ${input?.skill ?? ""}`);
      break;
    default:
      lines.push(`[tool] ${name}`);
      break;
  }

  return lines;
}
