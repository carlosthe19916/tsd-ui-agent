import axios from "axios";

import type { GitDto, New } from "./models";

const BASE_URL = "/api/gits";

export const getGits = () =>
  axios.get<GitDto[]>(BASE_URL).then((response) => response.data);

export const createGit = (git: New<GitDto>) =>
  axios.post<GitDto>(BASE_URL, git).then((response) => response.data);

export const updateGit = (id: number, git: GitDto) =>
  axios.put<GitDto>(`${BASE_URL}/${id}`, git).then((response) => response.data);

export const deleteGit = (id: number) =>
  axios.delete<void>(`${BASE_URL}/${id}`);
