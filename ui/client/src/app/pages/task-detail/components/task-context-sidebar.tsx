import React from "react";

import ReactMarkdown from "react-markdown";

import {
  DescriptionList,
  DescriptionListDescription,
  DescriptionListGroup,
  DescriptionListTerm,
  Flex,
  FlexItem,
  Icon,
  Label,
  LabelGroup,
  Panel,
  PanelMain,
  PanelMainBody,
  Content,
  Tab,
  Tabs,
  TabTitleText,
  Title,
} from "@patternfly/react-core";
import CheckCircleIcon from "@patternfly/react-icons/dist/esm/icons/check-circle-icon";
import InProgressIcon from "@patternfly/react-icons/dist/esm/icons/in-progress-icon";
import PendingIcon from "@patternfly/react-icons/dist/esm/icons/pending-icon";

import type { TaskDto } from "@app/api/models";
import { PlanProgressStepper } from "@app/pages/task-list/components/plan-progress-stepper";
import { formatDateTime } from "@app/utils/utils";

interface TaskContextSidebarProps {
  task: TaskDto;
}

const statusIcon = (status: string) => {
  switch (status) {
    case "OPEN":
      return <PendingIcon />;
    case "IN_PROGRESS":
      return <InProgressIcon />;
    case "CLOSED":
      return (
        <CheckCircleIcon color="var(--pf-t--global--color--status--success--default)" />
      );
    default:
      return <PendingIcon />;
  }
};

export const TaskContextSidebar: React.FC<TaskContextSidebarProps> = ({
  task,
}) => {
  const [activeTab, setActiveTab] = React.useState<string | number>(0);

  return (
    <Flex
      direction={{ default: "column" }}
      gap={{ default: "gapMd" }}
      style={{ height: "100%", overflow: "auto", padding: "0 16px" }}
    >
      {/* Task header */}
      <FlexItem>
        <Flex
          gap={{ default: "gapSm" }}
          alignItems={{ default: "alignItemsCenter" }}
        >
          <FlexItem>
            <Icon>{statusIcon(task.status)}</Icon>
          </FlexItem>
          <FlexItem>
            <Title headingLevel="h2" size="lg">
              {task.title}
            </Title>
          </FlexItem>
        </Flex>
        {task.url && (
          <a href={task.url} target="_blank" rel="noopener noreferrer">
            {task.type === "GITHUB" && "#"}
            {task.externalId}
          </a>
        )}
      </FlexItem>

      {/* Task metadata */}
      <FlexItem>
        <DescriptionList isCompact>
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
            <DescriptionListTerm>Workspace</DescriptionListTerm>
            <DescriptionListDescription>
              {task.workspace?.workspaceId ? (
                <Label color="green">Provisioned</Label>
              ) : (
                <Label color="grey">Not provisioned</Label>
              )}
            </DescriptionListDescription>
          </DescriptionListGroup>
          <DescriptionListGroup>
            <DescriptionListTerm>Updated</DescriptionListTerm>
            <DescriptionListDescription>
              {formatDateTime(task.updatedAt)}
            </DescriptionListDescription>
          </DescriptionListGroup>
        </DescriptionList>
      </FlexItem>

      {/* Plan progress */}
      {task.plan && (
        <FlexItem>
          <Title headingLevel="h3" size="md">
            Plan Progress
          </Title>
          <PlanProgressStepper
            taskId={task.id}
            plan={task.plan}
            workspace={task.workspace}
            onEditRequirement={() => {}}
            onEditPlan={() => {}}
          />
          {task.plan.changeRequestUrl && (
            <div style={{ marginTop: 8 }}>
              PR:{" "}
              <a
                href={task.plan.changeRequestUrl}
                target="_blank"
                rel="noopener noreferrer"
              >
                #{task.plan.changeRequestUrl.match(/\/(\d+)\/?$/)?.[1] ?? "PR"}
              </a>
            </div>
          )}
        </FlexItem>
      )}

      {/* Tabbed content */}
      <FlexItem grow={{ default: "grow" }} style={{ minHeight: 0 }}>
        <Tabs
          activeKey={activeTab}
          onSelect={(_ev, key) => setActiveTab(key)}
          isFilled
        >
          <Tab eventKey={0} title={<TabTitleText>Description</TabTitleText>}>
            <Panel isScrollable style={{ marginTop: 8 }}>
              <PanelMain tabIndex={0}>
                <PanelMainBody>
                  <Content>
                    <ReactMarkdown>{task.description ?? ""}</ReactMarkdown>
                  </Content>
                </PanelMainBody>
              </PanelMain>
            </Panel>
          </Tab>
          {task.plan?.requirement && (
            <Tab eventKey={1} title={<TabTitleText>Requirement</TabTitleText>}>
              <Panel isScrollable style={{ marginTop: 8 }}>
                <PanelMain tabIndex={0}>
                  <PanelMainBody>
                    <Content>
                      <ReactMarkdown>{task.plan.requirement}</ReactMarkdown>
                    </Content>
                  </PanelMainBody>
                </PanelMain>
              </Panel>
            </Tab>
          )}
          {task.plan?.plan && (
            <Tab eventKey={2} title={<TabTitleText>Plan</TabTitleText>}>
              <Panel isScrollable style={{ marginTop: 8 }}>
                <PanelMain tabIndex={0}>
                  <PanelMainBody>
                    <Content>
                      <ReactMarkdown>{task.plan.plan}</ReactMarkdown>
                    </Content>
                  </PanelMainBody>
                </PanelMain>
              </Panel>
            </Tab>
          )}
        </Tabs>
      </FlexItem>
    </Flex>
  );
};
