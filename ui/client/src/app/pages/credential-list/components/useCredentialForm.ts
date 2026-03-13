import { useForm } from "react-hook-form";

import { yupResolver } from "@hookform/resolvers/yup";
import * as yup from "yup";

import type { CredentialDto } from "@app/api/models";
import {
  useCreateCredentialMutation,
  useUpdateCredentialMutation,
} from "@app/queries/credentials";

export interface CredentialFormValues {
  name: string;
  token: string;
}

const schema = yup.object({
  name: yup.string().required("Name is required"),
  token: yup.string().required("Token is required"),
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
    token: credential.token,
  };
};

export const useCredentialForm = (
  credential: CredentialDto | null,
  onClose: () => void,
) => {
  const isEditing = !!credential?.id;

  const createMutation = useCreateCredentialMutation(onClose);
  const updateMutation = useUpdateCredentialMutation(onClose);

  const form = useForm<CredentialFormValues>({
    defaultValues: mapCredentialToFormValues(credential),
    resolver: yupResolver(schema),
    mode: "onChange",
  });

  const onSubmit = form.handleSubmit((values: CredentialFormValues) => {
    const dto: CredentialDto = {
      ...(isEditing && { id: credential.id }),
      name: values.name,
      token: values.token,
    };

    if (isEditing) {
      updateMutation.mutate(dto);
    } else {
      createMutation.mutate(dto);
    }
  });

  const isSubmitDisabled =
    !form.formState.isValid ||
    form.formState.isSubmitting ||
    (!form.formState.isDirty && isEditing);

  const isCancelDisabled = form.formState.isSubmitting;

  return { form, onSubmit, isSubmitDisabled, isCancelDisabled };
};
