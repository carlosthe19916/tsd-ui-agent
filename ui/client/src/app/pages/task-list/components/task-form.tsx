import React from "react";
import type { Control } from "react-hook-form";

import { Form } from "@patternfly/react-core";

import { HookFormPFTextInput } from "@app/components/HookFormPFFields";

import type { TaskFormValues } from "./useTaskForm";

interface TaskFormProps {
  control: Control<TaskFormValues>;
}

export const TaskForm: React.FC<TaskFormProps> = ({ control }) => {
  return (
    <Form>
      <HookFormPFTextInput
        control={control}
        name="title"
        label="Title"
        fieldId="task-title"
        isRequired
      />
    </Form>
  );
};
