import type React from "react";
import type { Control } from "react-hook-form";

import { Form } from "@patternfly/react-core";

import { HookFormPFTextInput } from "@app/components/HookFormPFFields";

import type { CredentialFormValues } from "./useCredentialForm";

interface CredentialFormProps {
  control: Control<CredentialFormValues>;
}

export const CredentialForm: React.FC<CredentialFormProps> = ({ control }) => {
  return (
    <Form>
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
        isRequired
        type="password"
      />
    </Form>
  );
};
