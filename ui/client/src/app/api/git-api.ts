import axios from "axios";

import type { ExecutionMode, GitDto, New, WorkspaceDto } from "./models";

const BASE_URL = "/api/gits";
const WORKSPACES_URL = "/api/workspaces";

export const getGits = () =>
  axios.get<GitDto[]>(BASE_URL).then((response) => response.data);

export const createGit = (git: New<GitDto>) =>
  axios.post<GitDto>(BASE_URL, git).then((response) => response.data);

export const updateGit = (id: number, git: GitDto) =>
  axios.put<GitDto>(`${BASE_URL}/${id}`, git).then((response) => response.data);

export const deleteGit = (id: number) =>
  axios.delete<void>(`${BASE_URL}/${id}`);

export const getWorkspaces = (gitId: number, hasTask?: boolean) =>
  axios
    .get<WorkspaceDto[]>(WORKSPACES_URL, { params: { gitId, hasTask } })
    .then((response) => response.data);

export const createWorkspace = (
  gitId: number,
  taskId?: number,
  executionMode?: ExecutionMode,
) =>
  axios
    .post<WorkspaceDto>(WORKSPACES_URL, {
      git: { id: gitId },
      ...(taskId ? { task: { id: taskId } } : {}),
      ...(executionMode ? { executionMode } : {}),
    })
    .then((response) => response.data);

export const deleteWorkspace = (wsId: number) =>
  axios.delete<void>(`${WORKSPACES_URL}/${wsId}`);

export const getWorkspaceStatus = (wsId: number) =>
  axios
    .get<{
      status: "RUNNING" | "STOPPED" | "ERROR";
      reason?: string;
      supportsStartStop: boolean;
    }>(`${WORKSPACES_URL}/${wsId}/status`)
    .then((response) => response.data);

export const getWorkspaceCommands = (wsId: number) =>
  axios
    .get<{ type: string; label: string; command: string }[]>(
      `${WORKSPACES_URL}/${wsId}/commands`,
    )
    .then((response) => response.data);

export const startWorkspace = (wsId: number) =>
  axios.post<void>(`${WORKSPACES_URL}/${wsId}/start`);

export const stopWorkspace = (wsId: number) =>
  axios.post<void>(`${WORKSPACES_URL}/${wsId}/stop`);

export const streamWorkspaceOutput = async function* (
  wsId: number,
  signal?: AbortSignal,
): AsyncGenerator<string> {
  const response = await fetch(`${WORKSPACES_URL}/${wsId}/output`, { signal });
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
};

export const streamGitOutput = async function* (
  gitId: number,
  signal?: AbortSignal,
): AsyncGenerator<string> {
  const response = await fetch(`${BASE_URL}/${gitId}/output`, { signal });
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
};
