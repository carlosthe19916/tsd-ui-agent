import { useForm } from "react-hook-form";
import { yupResolver } from "@hookform/resolvers/yup";
import * as yup from "yup";

import type { New, TaskDto } from "@app/api/models";
import { useCreateTaskMutation } from "@app/queries/tasks";

export interface TaskFormValues {
  title: string;
}

const schema = yup.object({
  title: yup.string().required("Title is required").min(1).max(255),
});

export const useTaskForm = (onClose: () => void) => {
  const createMutation = useCreateTaskMutation(onClose);

  const form = useForm<TaskFormValues>({
    defaultValues: { title: "" },
    resolver: yupResolver(schema),
    mode: "onChange",
  });

  const onSubmit = form.handleSubmit((values: TaskFormValues) => {
    const dto = {
      title: values.title,
    } as New<TaskDto>;
    createMutation.mutate(dto);
  });

  const isSubmitDisabled =
    !form.formState.isValid || form.formState.isSubmitting;

  const isCancelDisabled = form.formState.isSubmitting;

  return { form, onSubmit, isSubmitDisabled, isCancelDisabled };
};
