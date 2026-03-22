import axios from "axios";

import type { GitDto, New, WorkspaceDto } from "./models";

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

export const getWorkspaces = (gitId: number) =>
  axios
    .get<WorkspaceDto[]>(WORKSPACES_URL, { params: { gitId } })
    .then((response) => response.data);

export const createWorkspace = (gitId: number, taskId?: number) =>
  axios
    .post<WorkspaceDto>(WORKSPACES_URL, {
      git: { id: gitId },
      ...(taskId ? { task: { id: taskId } } : {}),
    })
    .then((response) => response.data);

export const deleteWorkspace = (wsId: number) =>
  axios.delete<void>(`${WORKSPACES_URL}/${wsId}`);

export const getWorkspaceStatus = (wsId: number) =>
  axios
    .get<{ status: "RUNNING" | "STOPPED" | "ERROR"; reason?: string }>(
      `${WORKSPACES_URL}/${wsId}/status`,
    )
    .then((response) => response.data);
