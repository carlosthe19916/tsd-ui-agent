import { useState } from "react";
import { useForm } from "react-hook-form";

import { yupResolver } from "@hookform/resolvers/yup";
import * as yup from "yup";

import type { CredentialDto, New } from "@app/api/models";
import { checkCredentialNameExists } from "@app/api/credential-api";
import {
  useCreateCredentialMutation,
  useUpdateCredentialMutation,
} from "@app/queries/credentials";

export interface CredentialFormValues {
  name: string;
  token: string;
}

const buildCredentialSchema = (excludeId?: number, isTokenRequired = true) =>
  yup.object({
    name: yup
      .string()
      .required("Name is required")
      .test(
        "unique-name",
        "A credential with this name already exists",
        async (value) => {
          if (!value) return true;
          const exists = await checkCredentialNameExists(value, excludeId);
          return !exists;
        },
      ),
    token: isTokenRequired
      ? yup.string().required("Token is required")
      : yup.string().defined(),
  });

const mapCredentialToFormValues = (
  credential: CredentialDto | null,
): CredentialFormValues => {
  if (!credential) {
    return {
      name: "",
      token: "",
    };
  }
  return {
    name: credential.name,
    token: credential.token ?? "",
  };
};

export const useCredentialForm = (
  credential: CredentialDto | null,
  onClose: () => void,
) => {
  const isEditing = !!credential?.id;
  const [isTokenEnabled, setIsTokenEnabled] = useState(!isEditing);

  const createMutation = useCreateCredentialMutation(onClose);
  const updateMutation = useUpdateCredentialMutation(onClose);

  const isTokenRequired = !isEditing || isTokenEnabled;
  const schema = buildCredentialSchema(credential?.id, isTokenRequired);

  const form = useForm<CredentialFormValues>({
    defaultValues: mapCredentialToFormValues(credential),
    resolver: yupResolver(schema),
    mode: "onChange",
  });

  const onSubmit = form.handleSubmit((values: CredentialFormValues) => {
    if (isEditing) {
      const dto: CredentialDto = {
        id: credential.id,
        name: values.name,
        token: values.token,
      };

      updateMutation.mutate(dto);
    } else {
      const dto: New<CredentialDto> = {
        name: values.name,
        ...(isTokenEnabled && values.token ? { token: values.token } : {}),
      };

      createMutation.mutate(dto);
    }
  });

  const isSubmitDisabled =
    !form.formState.isValid ||
    form.formState.isSubmitting ||
    (!form.formState.isDirty && isEditing);

  const isCancelDisabled = form.formState.isSubmitting;

  return {
    form,
    onSubmit,
    isSubmitDisabled,
    isCancelDisabled,
    isEditing,
    isTokenEnabled,
    setIsTokenEnabled,
  };
};
