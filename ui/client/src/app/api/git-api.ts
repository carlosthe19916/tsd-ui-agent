import axios from "axios";

import type { GitDto, New, WorkspaceDto } from "./models";

const BASE_URL = "/api/gits";

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
    .get<WorkspaceDto[]>(`${BASE_URL}/${gitId}/workspaces`)
    .then((response) => response.data);

export const createWorkspace = (gitId: number, taskId?: number) =>
  axios
    .post<WorkspaceDto>(
      `${BASE_URL}/${gitId}/workspaces`,
      taskId ? { task: { id: taskId } } : {},
    )
    .then((response) => response.data);
