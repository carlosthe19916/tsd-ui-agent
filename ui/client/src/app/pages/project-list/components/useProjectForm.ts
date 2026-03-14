import { useForm } from "react-hook-form";

import { yupResolver } from "@hookform/resolvers/yup";
import * as yup from "yup";

import type { New, ProjectDto, SourceType } from "@app/api/models";
import {
  useCreateProjectMutation,
  useUpdateProjectMutation,
} from "@app/queries/projects";

export interface ProjectFormValues {
  name: string;
  apiUrl: string;
  query: string;
  type: SourceType | "";
  gitUrl: string;
  gitBranch: string;
  credentialId: string;
}

const schema = yup.object({
  name: yup.string().required("Name is required"),
  apiUrl: yup.string().required("API URL is required"),
  query: yup
    .string()
    .when("type", ([typeVal], schema) =>
      typeVal === "JIRA"
        ? schema.required("Query is required for Jira projects")
        : schema.defined(),
    ),
  type: yup
    .string()
    .oneOf(["JIRA", "GITHUB"] as const, "Type is required")
    .required("Type is required"),
  gitUrl: yup
    .string()
    .required("Git URL is required")
    .matches(
      /^(https?:\/\/.+|git@[^:]+:.+|git:\/\/.+)$/,
      "Must be a valid Git URL (e.g. https://github.com/org/repo.git)",
    ),
  gitBranch: yup.string().defined(),
  credentialId: yup.string().required("Credential is required"),
});

const mapProjectToFormValues = (
  project: ProjectDto | null,
): ProjectFormValues => {
  if (!project) {
    return {
      name: "",
      apiUrl: "",
      query: "",
      type: "",
      gitUrl: "",
      gitBranch: "",
      credentialId: "",
    };
  }
  return {
    name: project.name,
    apiUrl: project.apiUrl,
    query: project.query ?? "",
    type: project.type,
    gitUrl: project.git.url,
    gitBranch: project.git.branch ?? "",
    credentialId: project.credential?.id?.toString() ?? "",
  };
};

export const useProjectForm = (
  project: ProjectDto | null,
  onClose: () => void,
) => {
  const isEditing = !!project?.id;

  const createMutation = useCreateProjectMutation(onClose);
  const updateMutation = useUpdateProjectMutation(onClose);

  const form = useForm<ProjectFormValues>({
    defaultValues: mapProjectToFormValues(project),
    resolver: yupResolver(schema),
    mode: "onChange",
  });

  const onSubmit = form.handleSubmit((values: ProjectFormValues) => {
    const dto: New<ProjectDto> = {
      name: values.name,
      apiUrl: values.apiUrl,
      query: values.query || undefined,
      type: values.type as SourceType,
      git: {
        url: values.gitUrl,
        branch: values.gitBranch || undefined,
      },
      credential: { id: Number(values.credentialId), name: "" },
    };

    if (isEditing) {
      updateMutation.mutate({ ...dto, id: project.id });
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
