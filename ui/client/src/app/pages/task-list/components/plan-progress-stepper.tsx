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
} from "@patternfly/react-core";

import type { PlanDto } from "@app/api/models";

interface PlanProgressStepperProps {
  taskId: number;
  plan: PlanDto;
  onEditStep: (step: number) => void;
}

export const PlanProgressStepper: React.FC<PlanProgressStepperProps> = ({
  taskId,
  plan,
  onEditStep,
}) => {
  return (
    <ProgressStepper>
      <ProgressStep
        variant={plan.requirement ? "success" : "pending"}
        id={`req-${taskId}`}
        titleId={`req-title-${taskId}`}
        aria-label={`Requirement step, ${plan.requirement ? "completed" : "pending"}`}
        description={
          plan.requirement
            ? plan.requirement.length > 40
              ? `${plan.requirement.substring(0, 40)}\u2026`
              : plan.requirement
            : "Not defined"
        }
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
