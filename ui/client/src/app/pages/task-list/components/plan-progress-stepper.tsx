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
}

const getRequirementStepVariant = (plan: PlanDto) => {
  if (plan.discoveryStatus === "IN_PROGRESS") return "info";
  if (plan.discoveryStatus === "ERROR") return "danger";
  if (plan.requirement) return "success";
  return "pending";
};

const getRequirementDescription = (plan: PlanDto) => {
  if (plan.discoveryStatus === "IN_PROGRESS") return "Discovering...";
  if (plan.discoveryStatus === "ERROR") {
    const err = plan.discoveryError ?? "Unknown error";
    return err.length > 40 ? `${err.substring(0, 40)}\u2026` : err;
  }
  if (plan.requirement) {
    return plan.requirement.length > 40
      ? `${plan.requirement.substring(0, 40)}\u2026`
      : plan.requirement;
  }
  return "Not defined";
};

export const PlanProgressStepper: React.FC<PlanProgressStepperProps> = ({
  taskId,
  plan,
  onEditStep,
}) => {
  const reqVariant = getRequirementStepVariant(plan);

  return (
    <ProgressStepper>
      <ProgressStep
        variant={reqVariant}
        icon={plan.discoveryStatus === "IN_PROGRESS" ? <Spinner size="sm" /> : undefined}
        id={`req-${taskId}`}
        titleId={`req-title-${taskId}`}
        aria-label={`Requirement step, ${reqVariant}`}
        description={getRequirementDescription(plan)}
        popoverRender={(stepRef) => (
          <Popover
            aria-label="Requirement details"
            headerContent={<div>Requirement</div>}
            bodyContent={
              <div>
                {plan.requirement || "No requirement has been defined yet."}
              </div>
            }
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
    </ProgressStepper>
  );
};
