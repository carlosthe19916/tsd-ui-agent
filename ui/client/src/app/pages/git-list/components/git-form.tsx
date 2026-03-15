import type React from "react";
import type { Control } from "react-hook-form";

import { Form } from "@patternfly/react-core";

import { HookFormPFTextInput } from "@app/components/HookFormPFFields";

import type { GitFormValues } from "./useGitForm";

interface GitFormProps {
  control: Control<GitFormValues>;
}

export const GitForm: React.FC<GitFormProps> = ({ control }) => {
  return (
    <Form>
      <HookFormPFTextInput
        control={control}
        name="url"
        label="URL"
        fieldId="url"
        isRequired
      />
      <HookFormPFTextInput
        control={control}
        name="branch"
        label="Branch"
        fieldId="branch"
        placeholder="Leave empty for default branch"
      />
      <HookFormPFTextInput
        control={control}
        name="forkUrl"
        label="Fork URL"
        fieldId="forkUrl"
        placeholder="Optional fork remote URL"
      />
    </Form>
  );
};
