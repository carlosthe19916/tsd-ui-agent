import type { CredentialDto } from "./models";

const BASE_URL = "/api/credentials";

export const getCredentials = async (): Promise<CredentialDto[]> => {
  const response = await fetch(BASE_URL);
  if (!response.ok) {
    throw new Error("Failed to fetch credentials");
  }
  return response.json();
};

export const createCredential = async (
  credential: CredentialDto,
): Promise<CredentialDto> => {
  const response = await fetch(BASE_URL, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(credential),
  });
  if (!response.ok) {
    throw new Error("Failed to create credential");
  }
  return response.json();
};

export const updateCredential = async (
  id: number,
  credential: CredentialDto,
): Promise<CredentialDto> => {
  const response = await fetch(`${BASE_URL}/${id}`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(credential),
  });
  if (!response.ok) {
    throw new Error("Failed to update credential");
  }
  return response.json();
};

export const deleteCredential = async (id: number): Promise<void> => {
  const response = await fetch(`${BASE_URL}/${id}`, {
    method: "DELETE",
  });
  if (!response.ok) {
    throw new Error("Failed to delete credential");
  }
};
