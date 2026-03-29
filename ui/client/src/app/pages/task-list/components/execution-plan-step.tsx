import React from "react";

import { yupResolver } from "@hookform/resolvers/yup";
import { useForm } from "react-hook-form";
import ReactMarkdown from "react-markdown";
import * as yup from "yup";

import { Language } from "@patternfly/react-code-editor";
import {
  Alert,
  Button,
  Content,
  Flex,
  Grid,
  GridItem,
  Panel,
  PanelMain,
  PanelMainBody,
  Spinner,
  ToggleGroup,
  ToggleGroupItem,
  Toolbar,
  ToolbarContent,
  ToolbarItem,
} from "@patternfly/react-core";
import CodeIcon from "@patternfly/react-icons/dist/esm/icons/code-icon";
import ColumnsIcon from "@patternfly/react-icons/dist/esm/icons/columns-icon";
import EyeIcon from "@patternfly/react-icons/dist/esm/icons/eye-icon";
import RobotIcon from "@patternfly/react-icons/dist/esm/icons/robot-icon";

import { ThemedCodeEditor } from "@app/components/ThemedCodeEditor";
import { useFormChangeHandler } from "@app/hooks/useFormChangeHandler";
import {
  useCancelPlanOperationMutation,
  useFetchTaskPlan,
  useGeneratePlanMutation,
} from "@app/queries/tasks";

interface PlanValues {
  plan: string;
}

export interface PlanState extends PlanValues {
  isValid: boolean;
}

const schema = yup.object({
  plan: yup.string().defined().default(""),
});

type ViewMode = "editor" | "preview" | "split";

interface PlanStepProps {
  taskId: number;
  hasGit: boolean;
  initialState: PlanState;
  onStateChanged: (state: PlanState) => void;
}

export const PlanStep: React.FC<PlanStepProps> = ({
  taskId,
  hasGit,
  initialState,
  onStateChanged,
}) => {
  const [viewMode, setViewMode] = React.useState<ViewMode>("editor");

  const form = useForm<PlanValues>({
    resolver: yupResolver(schema),
    mode: "all",
    defaultValues: { plan: initialState.plan },
  });

  useFormChangeHandler({ form, onStateChanged });

  const plan = form.watch("plan");

  const { data: planData } = useFetchTaskPlan(taskId);
  const generateMutation = useGeneratePlanMutation();
  const cancelMutation = useCancelPlanOperationMutation();

  const isGenerating = planData?.isPlanGenerationInProgress;

  React.useEffect(() => {
    if (
      !planData?.isPlanGenerationInProgress &&
      !planData?.planGenerationError &&
      planData?.plan
    ) {
      form.setValue("plan", planData.plan, {
        shouldValidate: true,
        shouldDirty: true,
      });
    }
  }, [
    planData?.plan,
    form,
    planData?.planGenerationError,
    planData?.isPlanGenerationInProgress,
  ]);

  return (
    <Flex direction={{ default: "column" }} flexWrap={{ default: "nowrap" }}>
      {!hasGit && (
        <Alert
          variant="info"
          isInline
          isPlain
          title="Git configuration is required to generate a plan with AI."
        />
      )}
      <Toolbar>
        <ToolbarContent>
          <ToolbarItem>
            <ToggleGroup aria-label="View mode">
              <ToggleGroupItem
                icon={<CodeIcon />}
                text="Editor"
                aria-label="Editor view"
                isSelected={viewMode === "editor"}
                onChange={() => setViewMode("editor")}
              />
              <ToggleGroupItem
                icon={<ColumnsIcon />}
                text="Split"
                aria-label="Split view"
                isSelected={viewMode === "split"}
                onChange={() => setViewMode("split")}
              />
              <ToggleGroupItem
                icon={<EyeIcon />}
                text="Preview"
                aria-label="Preview view"
                isSelected={viewMode === "preview"}
                onChange={() => setViewMode("preview")}
              />
            </ToggleGroup>
          </ToolbarItem>
          <ToolbarItem align={{ default: "alignEnd" }}>
            {isGenerating ? (
              <Button
                variant="danger"
                onClick={() => cancelMutation.mutate(taskId)}
                isLoading={cancelMutation.isPending}
              >
                Cancel Generation
              </Button>
            ) : (
              <Button
                variant="secondary"
                icon={<RobotIcon />}
                onClick={() => generateMutation.mutate(taskId)}
                isDisabled={!hasGit}
                isLoading={generateMutation.isPending}
              >
                Generate with AI
              </Button>
            )}
          </ToolbarItem>
        </ToolbarContent>
      </Toolbar>
      {isGenerating && (
        <Alert
          variant="info"
          isInline
          isPlain
          title={
            <>
              <Spinner size="sm" /> AI is generating the plan...
            </>
          }
        />
      )}
      {planData?.planGenerationError && (
        <Alert
          variant="danger"
          isInline
          isPlain
          title={planData.planGenerationError}
        />
      )}
      <Grid hasGutter>
        {viewMode !== "preview" && (
          <GridItem span={viewMode === "split" ? 6 : 12}>
            <ThemedCodeEditor
              language={Language.markdown}
              code={plan}
              onCodeChange={(value) =>
                form.setValue("plan", value, {
                  shouldValidate: true,
                  shouldDirty: true,
                })
              }
              height="65vh"
              isLineNumbersVisible
              isHeaderPlain
            />
          </GridItem>
        )}
        {viewMode !== "editor" && (
          <GridItem span={viewMode === "split" ? 6 : 12}>
            <Panel variant="bordered" isScrollable>
              <PanelMain tabIndex={0}>
                <PanelMainBody>
                  <Content>
                    <ReactMarkdown>{plan}</ReactMarkdown>
                  </Content>
                </PanelMainBody>
              </PanelMain>
            </Panel>
          </GridItem>
        )}
      </Grid>
    </Flex>
  );
};
