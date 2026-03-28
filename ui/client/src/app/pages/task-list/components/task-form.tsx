import type React from "react";
import type { Control } from "react-hook-form";

import { HookFormPFTextInput } from "@app/components/HookFormPFFields";

import type { TaskFormValues } from "./useTaskForm";

interface TaskFormProps {
  control: Control<TaskFormValues>;
}

export const TaskForm: React.FC<TaskFormProps> = ({ control }) => {
  return (
    <HookFormPFTextInput
      control={control}
      name="title"
      label="Title"
      fieldId="task-title"
      isRequired
    />
  );
};
