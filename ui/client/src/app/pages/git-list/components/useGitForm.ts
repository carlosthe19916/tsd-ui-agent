import { useMemo } from "react";
import { useForm } from "react-hook-form";

import { yupResolver } from "@hookform/resolvers/yup";
import type { AxiosError } from "axios";
import * as yup from "yup";

import type { GitDto, New } from "@app/api/models";
import { useCreateGitMutation, useUpdateGitMutation } from "@app/queries/gits";

export interface GitFormValues {
  url: string;
  branch: string;
  forkUrl: string;
}

const buildSchema = (existingGits: GitDto[], editId: number | undefined) =>
  yup.object({
    url: yup
      .string()
      .required("URL is required")
      .matches(
        /^(https?:\/\/.+|git@[^:]+:.+|git:\/\/.+)$/,
        "Must be a valid Git URL (e.g. https://github.com/org/repo.git or git@github.com:org/repo.git)",
      )
      .test(
        "unique-url-branch",
        "A git repository with this URL and branch already exists",
        (url, context) => {
          const branch = context.parent.branch || "";
          return !existingGits.some(
            (g) =>
              g.url === url && (g.branch ?? "") === branch && g.id !== editId,
          );
        },
      ),
    branch: yup
      .string()
      .defined()
      .default("")
      .test(
        "unique-branch-url",
        "A git repository with this URL and branch already exists",
        (branch, context) => {
          const normalizedBranch = branch || "";
          const url = context.parent.url;
          return !existingGits.some(
            (g) =>
              g.url === url &&
              (g.branch ?? "") === normalizedBranch &&
              g.id !== editId,
          );
        },
      ),
    forkUrl: yup
      .string()
      .defined()
      .default("")
      .test(
        "valid-git-url",
        "Must be a valid Git URL (e.g. https://github.com/org/repo.git or git@github.com:org/repo.git)",
        (value) => {
          if (!value) return true;
          return /^(https?:\/\/.+|git@[^:]+:.+|git:\/\/.+)$/.test(value);
        },
      ),
  });

const mapGitToFormValues = (git: GitDto | null): GitFormValues => {
  if (!git) {
    return {
      url: "",
      branch: "",
      forkUrl: "",
    };
  }
  return {
    url: git.url,
    branch: git.branch ?? "",
    forkUrl: git.forkUrl ?? "",
  };
};

export const useGitForm = (
  git: GitDto | null,
  existingGits: GitDto[],
  onClose: () => void,
) => {
  const isEditing = !!git?.id;

  const { mutateAsync: createGit } = useCreateGitMutation(onClose);
  const { mutateAsync: updateGit } = useUpdateGitMutation(onClose);

  const schema = useMemo(
    () => buildSchema(existingGits, isEditing ? git.id : undefined),
    [existingGits, isEditing, git?.id],
  );

  const form = useForm<GitFormValues>({
    defaultValues: mapGitToFormValues(git),
    resolver: yupResolver(schema),
    mode: "onChange",
  });

  const handleConflictError = (error: unknown) => {
    const axiosError = error as AxiosError<{ error: string }>;
    if (axiosError.response?.status === 409) {
      form.setError("url", {
        type: "manual",
        message:
          axiosError.response.data?.error ||
          "A git repository with this URL and branch already exists",
      });
    }
  };

  const onSubmit = form.handleSubmit((values: GitFormValues) => {
    if (isEditing) {
      const dto: GitDto = {
        id: git.id,
        url: values.url,
        branch: values.branch || undefined,
        forkUrl: values.forkUrl || undefined,
      };
      return updateGit(dto).catch(handleConflictError);
    } else {
      const dto: New<GitDto> = {
        url: values.url,
        branch: values.branch || undefined,
        forkUrl: values.forkUrl || undefined,
      };
      return createGit(dto).catch(handleConflictError);
    }
  });

  const isSubmitDisabled =
    !form.formState.isValid ||
    form.formState.isSubmitting ||
    (!form.formState.isDirty && isEditing);

  const isCancelDisabled = form.formState.isSubmitting;

  return { form, onSubmit, isSubmitDisabled, isCancelDisabled, isEditing };
};
