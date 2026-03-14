import { useState } from "react";
import { useForm } from "react-hook-form";

import { yupResolver } from "@hookform/resolvers/yup";
import * as yup from "yup";

import type { CredentialDto, New } from "@app/api/models";
import {
  useCreateCredentialMutation,
  useUpdateCredentialMutation,
} from "@app/queries/credentials";

export interface CredentialFormValues {
  name: string;
  token: string;
}

const createSchema = yup.object({
  name: yup.string().required("Name is required"),
  token: yup.string().required("Token is required"),
});

const editTokenDisabledSchema = yup.object({
  name: yup.string().required("Name is required"),
  token: yup.string().defined(),
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

  const schema =
    isEditing && !isTokenEnabled ? editTokenDisabledSchema : createSchema;

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
