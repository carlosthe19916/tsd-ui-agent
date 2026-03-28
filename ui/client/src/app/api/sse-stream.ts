export async function* readSSEStream(
  url: string,
  init?: RequestInit,
): AsyncGenerator<string> {
  const response = await fetch(url, init);
  if (!response.ok) throw new Error(`Stream failed: ${response.status}`);

  const reader = response.body?.getReader();
  if (!reader) return;

  const decoder = new TextDecoder();
  let buffer = "";

  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;

      buffer += decoder.decode(value, { stream: true });
      const lines = buffer.split("\n");
      buffer = lines.pop() || "";

      for (const line of lines) {
        if (line.startsWith("data:")) {
          yield line.slice(5);
        }
      }
    }
  } finally {
    reader.cancel();
  }
}
