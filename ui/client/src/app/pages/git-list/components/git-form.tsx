import type React from "react";
import type { Control } from "react-hook-form";

import { Checkbox, Form } from "@patternfly/react-core";

import { HookFormPFTextInput } from "@app/components/HookFormPFFields";

import type { GitFormValues } from "./useGitForm";

interface GitFormProps {
  control: Control<GitFormValues>;
  isEditing: boolean;
  isTokenEnabled: boolean;
  onToggleToken: (checked: boolean) => void;
}

export const GitForm: React.FC<GitFormProps> = ({
  control,
  isEditing,
  isTokenEnabled,
  onToggleToken,
}) => {
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
      <HookFormPFTextInput
        control={control}
        name="gitToken"
        label="API Token"
        fieldId="gitToken"
        placeholder="Optional token for PR/MR creation"
        type="password"
        isDisabled={isEditing && !isTokenEnabled}
      />
      {isEditing && (
        <Checkbox
          id="update-token"
          label="Update token"
          isChecked={isTokenEnabled}
          onChange={(_event, checked) => onToggleToken(checked)}
        />
      )}
    </Form>
  );
};
