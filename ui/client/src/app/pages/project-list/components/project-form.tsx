import React from "react";
import { useWatch } from "react-hook-form";
import type { Control } from "react-hook-form";

import {
  Alert,
  Button,
  FormSelect,
  FormSelectOption,
  InputGroup,
  InputGroupItem,
} from "@patternfly/react-core";

import {
  HookFormPFGroupController,
  HookFormPFSelect,
  HookFormPFTextInput,
  HookFormPFTypeaheadSelect,
  TypeaheadSelectInput,
} from "@app/components/HookFormPFFields";

import { useFetchCredentials } from "@app/queries/credentials";
import {
  testConnection,
  testQuery,
  type TestConnectionResponse,
} from "@app/api/project-api";
import type { SourceType } from "@app/api/models";

import type { ProjectFormValues } from "./useProjectForm";

interface ProjectFormProps {
  control: Control<ProjectFormValues>;
}

export const ProjectForm: React.FC<ProjectFormProps> = ({ control }) => {
  const { data: credentials } = useFetchCredentials();

  const [isTesting, setIsTesting] = React.useState(false);
  const [testResult, setTestResult] =
    React.useState<TestConnectionResponse | null>(null);

  const [isTestingQuery, setIsTestingQuery] = React.useState(false);
  const [testQueryResult, setTestQueryResult] =
    React.useState<TestConnectionResponse | null>(null);

  const type = useWatch({ control, name: "type" });
  const apiUrl = useWatch({ control, name: "apiUrl" });
  const query = useWatch({ control, name: "query" });
  const credentialId = useWatch({ control, name: "credentialId" });

  const isTestEnabled = !!type && !!apiUrl && !!credentialId;
  const isTestQueryEnabled = !!type && !!apiUrl && !!query && !!credentialId;

  // Clear test result when relevant fields change
  // biome-ignore lint/correctness/useExhaustiveDependencies: deps trigger reset on field change
  React.useEffect(() => {
    setTestResult(null);
  }, [type, apiUrl, credentialId]);

  // Clear test query result when relevant fields change
  // biome-ignore lint/correctness/useExhaustiveDependencies: deps trigger reset on field change
  React.useEffect(() => {
    setTestQueryResult(null);
  }, [type, apiUrl, query, credentialId]);

  const handleTestConnection = async () => {
    setIsTesting(true);
    setTestResult(null);
    try {
      const result = await testConnection({
        type: type as SourceType,
        apiUrl,
        query: query || undefined,
        credentialId: Number(credentialId),
      });
      setTestResult(result);
    } catch (error: unknown) {
      if (
        error &&
        typeof error === "object" &&
        "response" in error &&
        error.response &&
        typeof error.response === "object" &&
        "data" in error.response
      ) {
        const data = (error as { response: { data: TestConnectionResponse } })
          .response.data;
        setTestResult(data);
      } else {
        setTestResult({ status: "error", message: "Connection test failed" });
      }
    } finally {
      setIsTesting(false);
    }
  };

  const handleTestQuery = async () => {
    setIsTestingQuery(true);
    setTestQueryResult(null);
    try {
      const result = await testQuery({
        type: type as SourceType,
        apiUrl,
        query: query || undefined,
        credentialId: Number(credentialId),
      });
      setTestQueryResult(result);
    } catch (error: unknown) {
      if (
        error &&
        typeof error === "object" &&
        "response" in error &&
        error.response &&
        typeof error.response === "object" &&
        "data" in error.response
      ) {
        const data = (error as { response: { data: TestConnectionResponse } })
          .response.data;
        setTestQueryResult(data);
      } else {
        setTestQueryResult({ status: "error", message: "Query test failed" });
      }
    } finally {
      setIsTestingQuery(false);
    }
  };

  return (
    <>
      <HookFormPFTextInput
        control={control}
        name="name"
        label="Name"
        fieldId="name"
        isRequired
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
      <HookFormPFTypeaheadSelect
        control={control}
        name="apiUrl"
        label="API URL"
        fieldId="apiUrl"
        isRequired
        options={[
          "https://carlosthe19916-1773473418920.atlassian.net/",
          "https://issues.redhat.com/",
          "https://api.github.com/repos/carlosthe19916/tsd-ui-agent",
        ]}
        placeholder={
          type === "JIRA"
            ? "https://<company>.atlassian.net/"
            : type === "GITHUB"
              ? "https://api.github.com/repos/{org}/{repo}"
              : "API URL"
        }
      />
      <HookFormPFGroupController
        control={control}
        name="credentialId"
        label="Credential"
        fieldId="credentialId"
        isRequired
        renderInput={({ field: { onChange, onBlur, value, name, ref } }) => (
          <>
            <InputGroup>
              <InputGroupItem isFill>
                <FormSelect
                  ref={ref}
                  name={name}
                  id="credentialId"
                  isRequired
                  onChange={onChange}
                  onBlur={onBlur}
                  value={value}
                >
                  <FormSelectOption
                    value=""
                    label="Select a credential"
                    isDisabled
                  />
                  {credentials?.map((cred) => (
                    <FormSelectOption
                      key={cred.id}
                      value={String(cred.id)}
                      label={cred.name}
                    />
                  ))}
                </FormSelect>
              </InputGroupItem>
              <InputGroupItem>
                <Button
                  variant="secondary"
                  isDisabled={!isTestEnabled || isTesting}
                  isLoading={isTesting}
                  onClick={handleTestConnection}
                >
                  Test
                </Button>
              </InputGroupItem>
            </InputGroup>
            {testResult && (
              <Alert
                variant={testResult.status === "ok" ? "success" : "danger"}
                isInline
                isPlain
                title={
                  testResult.status === "ok"
                    ? "Connection successful"
                    : (testResult.message ?? "Connection failed")
                }
              />
            )}
          </>
        )}
      />

      <HookFormPFGroupController
        control={control}
        name="query"
        label="Query"
        fieldId="query"
        isRequired={type === "JIRA"}
        renderInput={({ field }) => (
          <>
            <InputGroup>
              <InputGroupItem isFill>
                <TypeaheadSelectInput
                  options={["project = KAN ORDER BY created DESC"]}
                  placeholder="Select or type a query"
                  value={field.value as string}
                  onChange={(val) => field.onChange(val)}
                  fieldId="query"
                />
              </InputGroupItem>
              <InputGroupItem>
                <Button
                  variant="secondary"
                  isDisabled={!isTestQueryEnabled || isTestingQuery}
                  isLoading={isTestingQuery}
                  onClick={handleTestQuery}
                >
                  Test
                </Button>
              </InputGroupItem>
            </InputGroup>
            {testQueryResult && (
              <Alert
                variant={testQueryResult.status === "ok" ? "success" : "danger"}
                isInline
                isPlain
                title={
                  testQueryResult.status === "ok"
                    ? "Query successful"
                    : (testQueryResult.message ?? "Query failed")
                }
              />
            )}
          </>
        )}
      />
    </>
  );
};
