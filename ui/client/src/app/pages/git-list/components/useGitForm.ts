import { useForm } from "react-hook-form";

import { yupResolver } from "@hookform/resolvers/yup";
import * as yup from "yup";

import type { GitDto, New } from "@app/api/models";
import {
  useCreateGitMutation,
  useUpdateGitMutation,
} from "@app/queries/gits";

export interface GitFormValues {
  url: string;
  branch: string;
}

const schema = yup.object({
  url: yup
    .string()
    .required("URL is required")
    .matches(
      /^(https?:\/\/.+|git@[^:]+:.+|git:\/\/.+)$/,
      "Must be a valid Git URL (e.g. https://github.com/org/repo.git or git@github.com:org/repo.git)",
    ),
  branch: yup.string().defined(),
});

const mapGitToFormValues = (git: GitDto | null): GitFormValues => {
  if (!git) {
    return {
      url: "",
      branch: "",
    };
  }
  return {
    url: git.url,
    branch: git.branch ?? "",
  };
};

export const useGitForm = (git: GitDto | null, onClose: () => void) => {
  const isEditing = !!git?.id;

  const createMutation = useCreateGitMutation(onClose);
  const updateMutation = useUpdateGitMutation(onClose);

  const form = useForm<GitFormValues>({
    defaultValues: mapGitToFormValues(git),
    resolver: yupResolver(schema),
    mode: "onChange",
  });

  const onSubmit = form.handleSubmit((values: GitFormValues) => {
    if (isEditing) {
      const dto: GitDto = {
        id: git.id,
        url: values.url,
        branch: values.branch || undefined,
      };
      updateMutation.mutate(dto);
    } else {
      const dto: New<GitDto> = {
        url: values.url,
        branch: values.branch || undefined,
      };
      createMutation.mutate(dto);
    }
  });

  const isSubmitDisabled =
    !form.formState.isValid ||
    form.formState.isSubmitting ||
    (!form.formState.isDirty && isEditing);

  const isCancelDisabled = form.formState.isSubmitting;

  return { form, onSubmit, isSubmitDisabled, isCancelDisabled, isEditing };
};
