import type React from "react";
import type { Control } from "react-hook-form";

import { Form, FormSelect, FormSelectOption } from "@patternfly/react-core";

import {
  HookFormPFGroupController,
  HookFormPFTextInput,
} from "@app/components/HookFormPFFields";
import { useFetchCredentials } from "@app/queries/credentials";

import type { GitFormValues } from "./useGitForm";

interface GitFormProps {
  control: Control<GitFormValues>;
}

export const GitForm: React.FC<GitFormProps> = ({ control }) => {
  const { data: credentials } = useFetchCredentials();

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
      <HookFormPFGroupController
        control={control}
        name="vendorType"
        label="Vendor type"
        fieldId="vendorType"
        renderInput={({ field: { onChange, onBlur, value, name, ref } }) => (
          <FormSelect
            ref={ref}
            name={name}
            id="vendorType"
            onChange={onChange}
            onBlur={onBlur}
            value={value}
          >
            <FormSelectOption value="" label="Auto-detect" isPlaceholder />
            <FormSelectOption value="GITHUB" label="GitHub" />
            <FormSelectOption value="GITLAB" label="GitLab" />
          </FormSelect>
        )}
      />
      <HookFormPFGroupController
        control={control}
        name="credentialId"
        label="Credential"
        fieldId="credentialId"
        renderInput={({ field: { onChange, onBlur, value, name, ref } }) => (
          <FormSelect
            ref={ref}
            name={name}
            id="credentialId"
            onChange={onChange}
            onBlur={onBlur}
            value={value}
          >
            <FormSelectOption
              value=""
              label="Select a credential"
              isPlaceholder
            />
            {credentials?.map((cred) => (
              <FormSelectOption
                key={cred.id}
                value={String(cred.id)}
                label={cred.name}
              />
            ))}
          </FormSelect>
        )}
      />
    </Form>
  );
};
