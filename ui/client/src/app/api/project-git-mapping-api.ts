import axios from "axios";

import type { New, ProjectGitMappingDto } from "./models";

const mappingsUrl = (projectId: number) =>
  `/api/projects/${projectId}/git-mappings`;

export const getMappings = (projectId: number) =>
  axios.get<ProjectGitMappingDto[]>(mappingsUrl(projectId)).then((r) => r.data);

export const createMapping = (
  projectId: number,
  mapping: New<ProjectGitMappingDto>,
) =>
  axios
    .post<ProjectGitMappingDto>(mappingsUrl(projectId), mapping)
    .then((r) => r.data);

export const deleteMapping = (projectId: number, mappingId: number) =>
  axios.delete<void>(`${mappingsUrl(projectId)}/${mappingId}`);
