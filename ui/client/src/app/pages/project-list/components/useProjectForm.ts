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
      credentialId: "",
    };
  }
  return {
    name: project.name,
    apiUrl: project.apiUrl,
    query: project.query ?? "",
    type: project.type,
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
