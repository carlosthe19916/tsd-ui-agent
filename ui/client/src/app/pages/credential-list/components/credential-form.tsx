import type React from "react";
import type { Control } from "react-hook-form";

import { Checkbox } from "@patternfly/react-core";

import { HookFormPFTextInput } from "@app/components/HookFormPFFields";

import type { CredentialFormValues } from "./useCredentialForm";

interface CredentialFormProps {
  control: Control<CredentialFormValues>;
  isEditing: boolean;
  isTokenEnabled: boolean;
  onToggleToken: (checked: boolean) => void;
}

export const CredentialForm: React.FC<CredentialFormProps> = ({
  control,
  isEditing,
  isTokenEnabled,
  onToggleToken,
}) => {
  return (
    <>
      <HookFormPFTextInput
        control={control}
        name="name"
        label="Name"
        fieldId="name"
        isRequired
      />
      <HookFormPFTextInput
        control={control}
        name="token"
        label="Token"
        fieldId="token"
        isRequired={!isEditing || isTokenEnabled}
        type="password"
        placeholder="email:token (Jira) or token (GitHub)"
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
    </>
  );
};
