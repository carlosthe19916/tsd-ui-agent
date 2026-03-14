import axios from "axios";

import type { CredentialDto, New } from "./models";

const BASE_URL = "/api/credentials";

export const getCredentials = () =>
  axios.get<CredentialDto[]>(BASE_URL).then((response) => response.data);

export const createCredential = (credential: New<CredentialDto>) =>
  axios
    .post<CredentialDto>(BASE_URL, credential)
    .then((response) => response.data);

export const updateCredential = (id: number, credential: CredentialDto) =>
  axios
    .put<CredentialDto>(`${BASE_URL}/${id}`, credential)
    .then((response) => response.data);

export const deleteCredential = (id: number) =>
  axios.delete<void>(`${BASE_URL}/${id}`);
