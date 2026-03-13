import type React from "react";
import type { Control } from "react-hook-form";

import { Form, FormSection, FormSelectOption } from "@patternfly/react-core";

import {
  HookFormPFSelect,
  HookFormPFTextInput,
} from "@app/components/HookFormPFFields";

import { useFetchCredentials } from "@app/queries/credentials";

import type { ProjectFormValues } from "./useProjectForm";

interface ProjectFormProps {
  control: Control<ProjectFormValues>;
}

export const ProjectForm: React.FC<ProjectFormProps> = ({ control }) => {
  const { data: credentials } = useFetchCredentials();

  return (
    <Form>
      <FormSection title="Project Info">
        <HookFormPFTextInput
          control={control}
          name="name"
          label="Name"
          fieldId="name"
          isRequired
        />
        <HookFormPFTextInput
          control={control}
          name="url"
          label="URL"
          fieldId="url"
          isRequired
        />
        <HookFormPFTextInput
          control={control}
          name="query"
          label="Query"
          fieldId="query"
        />
        <HookFormPFSelect
          control={control}
          name="type"
          label="Type"
          fieldId="type"
          isRequired
        >
          <FormSelectOption value="" label="Select a type" isDisabled />
          <FormSelectOption value="JIRA" label="JIRA" />
          <FormSelectOption value="GITHUB" label="GITHUB" />
        </HookFormPFSelect>
      </FormSection>

      <FormSection title="Git Info">
        <HookFormPFTextInput
          control={control}
          name="gitUrl"
          label="Git URL"
          fieldId="gitUrl"
          isRequired
        />
        <HookFormPFTextInput
          control={control}
          name="gitBranch"
          label="Git Branch"
          fieldId="gitBranch"
        />
      </FormSection>

      <FormSection title="Credential">
        <HookFormPFSelect
          control={control}
          name="credentialId"
          label="Credential"
          fieldId="credentialId"
          isRequired
        >
          <FormSelectOption value="" label="Select a credential" isDisabled />
          {credentials?.map((cred) => (
            <FormSelectOption
              key={cred.id}
              value={String(cred.id)}
              label={cred.name}
            />
          ))}
        </HookFormPFSelect>
      </FormSection>
    </Form>
  );
};
