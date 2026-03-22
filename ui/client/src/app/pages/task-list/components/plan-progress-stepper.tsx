import type React from "react";

import {
  Button,
  Popover,
  ProgressStep,
  ProgressStepper,
  Spinner,
} from "@patternfly/react-core";

import type { PlanDto, WorkspaceDto } from "@app/api/models";
import {
  useEnrichRequirementMutation,
  useExecutePlanMutation,
  useGeneratePlanMutation,
  useOpenClaudeMutation,
} from "@app/queries/tasks";

interface PlanProgressStepperProps {
  taskId: number;
  plan: PlanDto;
  workspace?: WorkspaceDto;
  onEditRequirement: () => void;
  onEditPlan: () => void;
  onChangeRequest?: () => void;
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

const getPlanStepVariant = (plan: PlanDto) => {
  if (plan.isPlanGenerationInProgress) return "info";
  if (plan.planGenerationError) return "danger";
  if (plan.plan && plan.plan.trim().length > 0) return "success";
  return "pending";
};

const getPlanDescription = (plan: PlanDto) => {
  if (plan.isPlanGenerationInProgress) return "Generating...";
  if (plan.planGenerationError) {
    const err = plan.planGenerationError;
    return err.length > 40 ? `${err.substring(0, 40)}\u2026` : err;
  }
  return plan.plan && plan.plan.trim().length > 0 ? "" : "Not defined";
};

const getExecutionStepVariant = (plan: PlanDto) => {
  if (plan.isExecutionPlanInProgress) return "info";
  if (plan.executionPlanError) return "danger";
  if (plan.executionPlanCompletedAt) return "success";
  return "pending";
};

const getChangeRequestStepVariant = (plan: PlanDto) => {
  if (plan.isChangeRequestInProgress) return "info";
  if (plan.changeRequestError) return "danger";
  if (plan.changeRequestUrl) return "success";
  return "pending";
};

const getChangeRequestDescription = (plan: PlanDto) => {
  if (plan.isChangeRequestInProgress) return "Creating PR...";
  if (plan.changeRequestError) {
    const err = plan.changeRequestError;
    return err.length > 40 ? `${err.substring(0, 40)}\u2026` : err;
  }
  if (plan.changeRequestUrl) return "PR created";
  return "Not created";
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
  workspace,
  onEditRequirement,
  onEditPlan,
  onChangeRequest,
}) => {
  const enrichMutation = useEnrichRequirementMutation();
  const generatePlanMutation = useGeneratePlanMutation();
  const executePlanMutation = useExecutePlanMutation();
  const openClaudeMutation = useOpenClaudeMutation();
  const reqVariant = getRequirementStepVariant(plan);
  const planVariant = getPlanStepVariant(plan);

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
              <div style={{ display: "flex", gap: 16 }}>
                <Button variant="link" isInline onClick={onEditRequirement}>
                  {plan.requirement ? "Edit requirement" : "Add requirement"}
                </Button>
                <Button
                  variant="link"
                  isInline
                  onClick={() => enrichMutation.mutate(taskId)}
                  isDisabled={plan.isRequirementInProgress}
                  isLoading={enrichMutation.isPending}
                >
                  Enrich with AI
                </Button>
              </div>
            }
            triggerRef={stepRef}
          />
        )}
      >
        Requirement
      </ProgressStep>
      <ProgressStep
        variant={planVariant}
        icon={
          plan.isPlanGenerationInProgress ? <Spinner size="sm" /> : undefined
        }
        id={`exec-plan-${taskId}`}
        titleId={`exec-plan-title-${taskId}`}
        aria-label={`Plan step, ${planVariant}`}
        description={getPlanDescription(plan)}
        popoverRender={(stepRef) => (
          <Popover
            aria-label="Plan details"
            headerContent={<div>Plan</div>}
            bodyContent={
              plan.planGenerationError ? (
                <div>{plan.planGenerationError}</div>
              ) : plan.isPlanGenerationInProgress ? (
                <div>AI is generating the plan...</div>
              ) : plan.plan && plan.plan.trim().length > 0 ? (
                <div>Too long to render here</div>
              ) : (
                <div>No plan has been defined yet.</div>
              )
            }
            footerContent={
              <div style={{ display: "flex", gap: 16 }}>
                <Button variant="link" isInline onClick={onEditPlan}>
                  {plan.plan && plan.plan.trim().length > 0
                    ? "Edit plan"
                    : "Add plan"}
                </Button>
                <Button
                  variant="link"
                  isInline
                  onClick={() => generatePlanMutation.mutate(taskId)}
                  isDisabled={
                    !workspace?.git ||
                    !plan.requirement?.trim() ||
                    plan.isPlanGenerationInProgress
                  }
                  isLoading={generatePlanMutation.isPending}
                >
                  Generate with AI
                </Button>
              </div>
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
              <div style={{ display: "flex", gap: 16 }}>
                <Button
                  variant="link"
                  isInline
                  onClick={() => openClaudeMutation.mutate(taskId)}
                  isDisabled={
                    !workspace?.git ||
                    !plan.plan?.trim() ||
                    openClaudeMutation.isPending
                  }
                  isLoading={openClaudeMutation.isPending}
                >
                  Execute manually
                </Button>
                <Button
                  variant="link"
                  isInline
                  onClick={() => executePlanMutation.mutate(taskId)}
                  isDisabled={
                    !workspace?.git ||
                    !plan.plan?.trim() ||
                    plan.isExecutionPlanInProgress
                  }
                  isLoading={executePlanMutation.isPending}
                >
                  Execute with AI
                </Button>
              </div>
            }
            triggerRef={stepRef}
          />
        )}
      >
        Execution
      </ProgressStep>
      <ProgressStep
        variant={getChangeRequestStepVariant(plan)}
        icon={
          plan.isChangeRequestInProgress ? <Spinner size="sm" /> : undefined
        }
        id={`change-request-${taskId}`}
        titleId={`change-request-title-${taskId}`}
        aria-label={`Change request step, ${getChangeRequestStepVariant(plan)}`}
        description={getChangeRequestDescription(plan)}
        popoverRender={(stepRef) => (
          <Popover
            aria-label="Change request details"
            headerContent={<div>Change Request</div>}
            bodyContent={
              plan.changeRequestError ? (
                <div>{plan.changeRequestError}</div>
              ) : plan.isChangeRequestInProgress ? (
                <div>Creating pull request...</div>
              ) : plan.changeRequestUrl ? (
                <div>
                  <a
                    href={plan.changeRequestUrl}
                    target="_blank"
                    rel="noopener noreferrer"
                  >
                    View Pull Request
                  </a>
                </div>
              ) : !workspace?.git?.credential ? (
                <div>
                  No credential is configured in the git settings. A credential
                  is required to create pull/merge requests.
                </div>
              ) : (
                <div>No pull request has been created yet.</div>
              )
            }
            footerContent={
              plan.changeRequestUrl ? (
                <Button
                  variant="link"
                  isInline
                  component="a"
                  href={plan.changeRequestUrl}
                  target="_blank"
                >
                  Open PR
                </Button>
              ) : (
                <Button
                  variant="link"
                  isInline
                  onClick={() => onChangeRequest?.()}
                  isDisabled={
                    !plan.executionPlanCompletedAt ||
                    plan.isChangeRequestInProgress ||
                    !workspace?.git?.credential
                  }
                >
                  Create PR
                </Button>
              )
            }
            triggerRef={stepRef}
          />
        )}
      >
        Change Request
      </ProgressStep>
    </ProgressStepper>
  );
};
