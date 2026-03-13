import { useForm } from "react-hook-form";

import { yupResolver } from "@hookform/resolvers/yup";
import * as yup from "yup";

import type { New, ProjectDto } from "@app/api/models";
import {
  useCreateProjectMutation,
  useUpdateProjectMutation,
} from "@app/queries/projects";

export interface ProjectFormValues {
  name: string;
  url: string;
  query: string;
  type: string;
  gitUrl: string;
  gitBranch: string;
  credentialId: string;
}

const schema = yup.object({
  name: yup.string().required("Name is required"),
  url: yup.string().required("URL is required"),
  query: yup.string().defined(),
  type: yup.string().required("Type is required"),
  gitUrl: yup.string().required("Git URL is required"),
  gitBranch: yup.string().defined(),
  credentialId: yup.string().required("Credential is required"),
});

const mapProjectToFormValues = (
  project: ProjectDto | null,
): ProjectFormValues => {
  if (!project) {
    return {
      name: "",
      url: "",
      query: "",
      type: "",
      gitUrl: "",
      gitBranch: "",
      credentialId: "",
    };
  }
  return {
    name: project.name,
    url: project.url,
    query: project.query ?? "",
    type: project.type,
    gitUrl: project.git.url,
    gitBranch: project.git.branch ?? "",
    credentialId: project.credentialId?.toString() ?? "",
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
      url: values.url,
      query: values.query || undefined,
      type: values.type as ProjectDto["type"],
      git: {
        url: values.gitUrl,
        branch: values.gitBranch || undefined,
      },
      credentialId: Number(values.credentialId),
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
