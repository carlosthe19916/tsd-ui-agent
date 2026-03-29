import React from "react";

import {
  Button,
  DescriptionList,
  DescriptionListDescription,
  DescriptionListGroup,
  DescriptionListTerm,
  Divider,
  Flex,
  FlexItem,
  Grid,
  GridItem,
  Label,
  LabelGroup,
} from "@patternfly/react-core";
import TerminalIcon from "@patternfly/react-icons/dist/esm/icons/terminal-icon";

import type { TaskDto } from "@app/api/models";
import { ExecutionOutputModal } from "@app/pages/task-list/components/execution-output-modal";
import { PlanProgressStepper } from "@app/pages/task-list/components/plan-progress-stepper";
import {
  PlanModal,
  RequirementModal,
} from "@app/pages/task-list/components/plan-wizard-modal";
import { useTaskPlanActions } from "@app/pages/task-list/components/use-task-plan-actions";
import { WorkspaceCell } from "@app/pages/task-list/components/workspace-cell";
import { useCreateChangeRequestMutation } from "@app/queries/tasks";
import { formatDateTime } from "@app/utils/utils";

interface TaskContextSidebarProps {
  task: TaskDto;
}

export const TaskContextSidebar: React.FC<TaskContextSidebarProps> = ({
  task,
}) => {
  const [requirementTask, setRequirementTask] = React.useState<TaskDto | null>(
    null,
  );
  const [planTask, setPlanTask] = React.useState<TaskDto | null>(null);
  const [outputTaskId, setOutputTaskId] = React.useState<number | null>(null);

  const {
    setCreatePlanTask,
    setRunAllTask,
    dialogs: planActionDialogs,
  } = useTaskPlanActions();
  const changeRequestMutation = useCreateChangeRequestMutation();

  return (
    <>
      <Grid hasGutter>
        <GridItem md={6}>
          <DescriptionList isCompact>
            <DescriptionListGroup>
              <DescriptionListTerm>External Id</DescriptionListTerm>
              <DescriptionListDescription>
                {task.url && (
                  <a href={task.url} target="_blank" rel="noopener noreferrer">
                    {task.type === "GITHUB" && "#"}
                    {task.externalId}
                  </a>
                )}
              </DescriptionListDescription>
            </DescriptionListGroup>
            <DescriptionListGroup>
              <DescriptionListTerm>Project</DescriptionListTerm>
              <DescriptionListDescription>
                {task.project?.name ?? "-"}
              </DescriptionListDescription>
            </DescriptionListGroup>
            <DescriptionListGroup>
              <DescriptionListTerm>Status</DescriptionListTerm>
              <DescriptionListDescription>
                <Label>{task.externalStatus ?? task.status}</Label>
              </DescriptionListDescription>
            </DescriptionListGroup>
            {task.labels && task.labels.length > 0 && (
              <DescriptionListGroup>
                <DescriptionListTerm>Labels</DescriptionListTerm>
                <DescriptionListDescription>
                  <LabelGroup>
                    {task.labels.map((label) => (
                      <Label key={label}>{label}</Label>
                    ))}
                  </LabelGroup>
                </DescriptionListDescription>
              </DescriptionListGroup>
            )}
            <DescriptionListGroup>
              <DescriptionListTerm>Updated</DescriptionListTerm>
              <DescriptionListDescription>
                {formatDateTime(task.updatedAt)}
              </DescriptionListDescription>
            </DescriptionListGroup>
          </DescriptionList>
        </GridItem>
        {/* Workspace */}
        <GridItem md={6}>
          <DescriptionList isCompact>
            <DescriptionListGroup>
              <DescriptionListTerm>Workspace</DescriptionListTerm>
              <DescriptionListDescription>
                <WorkspaceCell task={task} />
              </DescriptionListDescription>
            </DescriptionListGroup>
          </DescriptionList>
        </GridItem>
        {/* Plan progress */}
        <Divider />
        <GridItem md={12}>
          {!task.plan && (
            <FlexItem>
              <Flex gap={{ default: "gapSm" }}>
                <FlexItem>
                  <Button
                    variant="secondary"
                    onClick={() => setCreatePlanTask(task)}
                  >
                    Create plan
                  </Button>
                </FlexItem>
                {task.workspace?.workspaceId && (
                  <FlexItem>
                    <Button
                      variant="primary"
                      onClick={() => setRunAllTask(task)}
                    >
                      Create plan and run
                    </Button>
                  </FlexItem>
                )}
              </Flex>
            </FlexItem>
          )}

          {task.plan && (
            <FlexItem>
              <DescriptionList isCompact>
                <DescriptionListGroup>
                  <DescriptionListTerm>Plan Progress</DescriptionListTerm>
                  <DescriptionListDescription>
                    <PlanProgressStepper
                      taskId={task.id}
                      plan={task.plan}
                      workspace={task.workspace}
                      onEditRequirement={() => setRequirementTask(task)}
                      onEditPlan={() => setPlanTask(task)}
                      onChangeRequest={() =>
                        changeRequestMutation.mutate(task.id)
                      }
                    />
                    {task.plan.changeRequestUrl && (
                      <div style={{ marginTop: 8 }}>
                        PR:{" "}
                        <a
                          href={task.plan.changeRequestUrl}
                          target="_blank"
                          rel="noopener noreferrer"
                        >
                          #
                          {task.plan.changeRequestUrl.match(
                            /\/(\d+)\/?$/,
                          )?.[1] ?? "PR"}
                        </a>
                      </div>
                    )}
                    {(task.plan.isPlanGenerationInProgress ||
                      task.plan.isExecutionPlanInProgress) && (
                      <div style={{ marginTop: 8 }}>
                        <Button
                          variant="link"
                          size="sm"
                          icon={<TerminalIcon />}
                          onClick={() => setOutputTaskId(task.id)}
                        >
                          View Output
                        </Button>
                      </div>
                    )}
                  </DescriptionListDescription>
                </DescriptionListGroup>
              </DescriptionList>
            </FlexItem>
          )}
        </GridItem>
      </Grid>

      {/* Modals */}
      <RequirementModal
        task={requirementTask}
        isOpen={requirementTask !== null}
        onClose={() => setRequirementTask(null)}
      />

      <PlanModal
        task={planTask}
        isOpen={planTask !== null}
        onClose={() => setPlanTask(null)}
      />

      <ExecutionOutputModal
        taskId={outputTaskId}
        isOpen={outputTaskId !== null}
        onClose={() => setOutputTaskId(null)}
      />

      {planActionDialogs}
    </>
  );
};
