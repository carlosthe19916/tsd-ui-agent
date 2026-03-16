import type React from "react";

import {
  Button,
  DescriptionList,
  DescriptionListDescription,
  DescriptionListGroup,
  DescriptionListTerm,
  Popover,
  ProgressStep,
  ProgressStepper,
  Spinner,
} from "@patternfly/react-core";

import type { PlanDto } from "@app/api/models";

interface PlanProgressStepperProps {
  taskId: number;
  plan: PlanDto;
  onEditStep: (step: number) => void;
  onExecute?: () => void;
}

const getRequirementStepVariant = (plan: PlanDto) => {
  if (plan.isRequirementInProgress) return "info";
  if (plan.requirementError) return "danger";
  if (plan.requirement) return "success";
  return "pending";
};

const getRequirementDescription = (plan: PlanDto) => {
  if (plan.isRequirementInProgress) return "Discovering...";
  if (plan.requirementError) {
    const err = plan.requirementError;
    return err.length > 40 ? `${err.substring(0, 40)}\u2026` : err;
  }
  return plan.requirement && plan.requirement.trim().length > 0
    ? ""
    : "Not defined";
};

const getRequirementPopupContent = (plan: PlanDto) => {
  if (plan.isRequirementInProgress)
    return "No requirement has been defined yet.";
  if (plan.requirementError) {
    return plan.requirementError;
  }
  return plan.requirement && plan.requirement.trim().length > 0
    ? "Too long to render here"
    : "Not defined";
};

const getExecutionStepVariant = (plan: PlanDto) => {
  if (plan.isExecutionPlanInProgress) return "info";
  if (plan.executionPlanError) return "danger";
  if (plan.executionPlanCompletedAt) return "success";
  return "pending";
};

const getExecutionDescription = (plan: PlanDto) => {
  if (plan.isExecutionPlanInProgress) return "Executing...";
  if (plan.executionPlanError) {
    const err = plan.executionPlanError;
    return err.length > 40 ? `${err.substring(0, 40)}\u2026` : err;
  }
  if (plan.executionPlanCompletedAt) return "Completed";
  return "Not executed";
};

export const PlanProgressStepper: React.FC<PlanProgressStepperProps> = ({
  taskId,
  plan,
  onEditStep,
  onExecute,
}) => {
  const reqVariant = getRequirementStepVariant(plan);

  return (
    <ProgressStepper>
      <ProgressStep
        variant={reqVariant}
        icon={plan.isRequirementInProgress ? <Spinner size="sm" /> : undefined}
        id={`req-${taskId}`}
        titleId={`req-title-${taskId}`}
        aria-label={`Requirement step, ${reqVariant}`}
        description={getRequirementDescription(plan)}
        popoverRender={(stepRef) => (
          <Popover
            aria-label="Requirement details"
            headerContent={<div>Requirement</div>}
            bodyContent={<div>{getRequirementPopupContent(plan)}</div>}
            footerContent={
              <Button variant="link" isInline onClick={() => onEditStep(1)}>
                {plan.requirement ? "Edit requirement" : "Add requirement"}
              </Button>
            }
            triggerRef={stepRef}
          />
        )}
      >
        Requirement
      </ProgressStep>
      <ProgressStep
        variant={plan.git ? "success" : "pending"}
        id={`git-${taskId}`}
        titleId={`git-title-${taskId}`}
        aria-label={`Git configuration step, ${plan.git ? "completed" : "pending"}`}
        description={plan.git ? plan.git.url : "Not configured"}
        popoverRender={(stepRef) => (
          <Popover
            aria-label="Git configuration details"
            headerContent={<div>Git Configuration</div>}
            bodyContent={
              plan.git ? (
                <DescriptionList isCompact>
                  <DescriptionListGroup>
                    <DescriptionListTerm>URL</DescriptionListTerm>
                    <DescriptionListDescription>
                      {plan.git.url}
                    </DescriptionListDescription>
                  </DescriptionListGroup>
                  {plan.git.branch && (
                    <DescriptionListGroup>
                      <DescriptionListTerm>Branch</DescriptionListTerm>
                      <DescriptionListDescription>
                        {plan.git.branch}
                      </DescriptionListDescription>
                    </DescriptionListGroup>
                  )}
                  {plan.git.forkUrl && (
                    <DescriptionListGroup>
                      <DescriptionListTerm>Fork URL</DescriptionListTerm>
                      <DescriptionListDescription>
                        {plan.git.forkUrl}
                      </DescriptionListDescription>
                    </DescriptionListGroup>
                  )}
                </DescriptionList>
              ) : (
                <div>No git repository has been configured yet.</div>
              )
            }
            footerContent={
              <Button variant="link" isInline onClick={() => onEditStep(2)}>
                {plan.git ? "Edit configuration" : "Configure"}
              </Button>
            }
            triggerRef={stepRef}
          />
        )}
      >
        Git Configuration
      </ProgressStep>
      <ProgressStep
        variant={
          plan.plan && plan.plan.trim().length > 0 ? "success" : "pending"
        }
        id={`exec-plan-${taskId}`}
        titleId={`exec-plan-title-${taskId}`}
        aria-label={`Plan step, ${plan.plan && plan.plan.trim().length > 0 ? "completed" : "pending"}`}
        description={
          plan.plan && plan.plan.trim().length > 0 ? "" : "Not defined"
        }
        popoverRender={(stepRef) => (
          <Popover
            aria-label="Plan details"
            headerContent={<div>Plan</div>}
            bodyContent={
              plan.plan && plan.plan.trim().length > 0 ? (
                <div>Too long to render here</div>
              ) : (
                <div>No plan has been defined yet.</div>
              )
            }
            footerContent={
              <Button variant="link" isInline onClick={() => onEditStep(3)}>
                {plan.plan && plan.plan.trim().length > 0
                  ? "Edit plan"
                  : "Add plan"}
              </Button>
            }
            triggerRef={stepRef}
          />
        )}
      >
        Plan
      </ProgressStep>
      <ProgressStep
        variant={getExecutionStepVariant(plan)}
        icon={
          plan.isExecutionPlanInProgress ? <Spinner size="sm" /> : undefined
        }
        id={`execution-${taskId}`}
        titleId={`execution-title-${taskId}`}
        aria-label={`Execution step, ${getExecutionStepVariant(plan)}`}
        description={getExecutionDescription(plan)}
        popoverRender={(stepRef) => (
          <Popover
            aria-label="Execution details"
            headerContent={<div>Execution</div>}
            bodyContent={
              plan.executionPlanError ? (
                <div>{plan.executionPlanError}</div>
              ) : plan.isExecutionPlanInProgress ? (
                <div>Plan is currently being executed by Claude CLI...</div>
              ) : plan.executionPlanCompletedAt ? (
                <div>Execution completed successfully.</div>
              ) : (
                <div>Plan has not been executed yet.</div>
              )
            }
            footerContent={
              <Button
                variant="link"
                isInline
                onClick={() => onExecute?.()}
                isDisabled={
                  !plan.git ||
                  !plan.plan?.trim() ||
                  plan.isExecutionPlanInProgress
                }
              >
                Execute plan
              </Button>
            }
            triggerRef={stepRef}
          />
        )}
      >
        Execution
      </ProgressStep>
    </ProgressStepper>
  );
};
