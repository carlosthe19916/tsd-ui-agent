import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import {
  createCredential,
  deleteCredential,
  getCredentials,
  updateCredential,
} from "@app/api/credential-api";
import type { CredentialDto } from "@app/api/models";

const CREDENTIAL_QUERY_KEY = "credentials";

export const useFetchCredentials = () => {
  return useQuery({
    queryKey: [CREDENTIAL_QUERY_KEY],
    queryFn: getCredentials,
  });
};

export const useCreateCredentialMutation = (onSuccess?: () => void) => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (credential: CredentialDto) => createCredential(credential),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [CREDENTIAL_QUERY_KEY] });
      onSuccess?.();
    },
  });
};

export const useUpdateCredentialMutation = (onSuccess?: () => void) => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (credential: CredentialDto) =>
      updateCredential(credential.id as number, credential),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [CREDENTIAL_QUERY_KEY] });
      onSuccess?.();
    },
  });
};

export const useDeleteCredentialMutation = (onSuccess?: () => void) => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => deleteCredential(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [CREDENTIAL_QUERY_KEY] });
      onSuccess?.();
    },
  });
};
