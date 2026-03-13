import type { ProjectDto } from "./models";

const BASE_URL = "/api/projects";

export const getProjects = async (): Promise<ProjectDto[]> => {
  const response = await fetch(BASE_URL);
  if (!response.ok) {
    throw new Error("Failed to fetch projects");
  }
  return response.json();
};

export const getProjectById = async (id: number): Promise<ProjectDto> => {
  const response = await fetch(`${BASE_URL}/${id}`);
  if (!response.ok) {
    throw new Error(`Failed to fetch project ${id}`);
  }
  return response.json();
};

export const createProject = async (
  project: ProjectDto,
): Promise<ProjectDto> => {
  const response = await fetch(BASE_URL, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(project),
  });
  if (!response.ok) {
    throw new Error("Failed to create project");
  }
  return response.json();
};

export const updateProject = async (
  id: number,
  project: ProjectDto,
): Promise<ProjectDto> => {
  const response = await fetch(`${BASE_URL}/${id}`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(project),
  });
  if (!response.ok) {
    throw new Error("Failed to update project");
  }
  return response.json();
};

export const deleteProject = async (id: number): Promise<void> => {
  const response = await fetch(`${BASE_URL}/${id}`, {
    method: "DELETE",
  });
  if (!response.ok) {
    throw new Error("Failed to delete project");
  }
};
