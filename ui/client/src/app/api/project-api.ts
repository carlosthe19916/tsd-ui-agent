import axios from "axios";

import type { New, ProjectDto } from "./models";

const BASE_URL = "/api/projects";

export const getProjects = () =>
  axios.get<ProjectDto[]>(BASE_URL).then((response) => response.data);

export const getProjectById = (id: number) =>
  axios.get<ProjectDto>(`${BASE_URL}/${id}`).then((response) => response.data);

export const createProject = (project: New<ProjectDto>) =>
  axios.post<ProjectDto>(BASE_URL, project).then((response) => response.data);

export const updateProject = (id: number, project: ProjectDto) =>
  axios
    .put<ProjectDto>(`${BASE_URL}/${id}`, project)
    .then((response) => response.data);

export const deleteProject = (id: number) =>
  axios.delete<void>(`${BASE_URL}/${id}`);
