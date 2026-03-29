import type React from "react";
import { useState } from "react";

import { useParams, Link } from "react-router-dom";

import {
  Breadcrumb,
  BreadcrumbItem,
  Content,
  Flex,
  FlexItem,
  PageSection,
  Title,
} from "@patternfly/react-core";

import { Paths } from "@app/Routes";
import { AsyncStateRenderer } from "@app/components/AsyncStateRenderer";
import { PageDrawerContent } from "@app/components/PageDrawerContext";
import { ThemedTerminal } from "@app/components/ThemedTerminal";
import { useFetchTask } from "@app/queries/tasks";

import { GitDiffDrawer } from "./components/git-diff-drawer";
import { TaskActionBar } from "./components/task-action-bar";
import { TaskContextSidebar } from "./components/task-context-sidebar";

export const TaskDetail: React.FC = () => {
  const { taskId } = useParams<{ taskId: string }>();
  const taskIdNum = Number(taskId);

  const { data: task, isLoading, isError } = useFetchTask(taskIdNum);
  const [isGitDrawerOpen, setIsGitDrawerOpen] = useState(false);
  const [isTerminalOpen, setIsTerminalOpen] = useState(false);

  return (
    <AsyncStateRenderer isLoading={isLoading} isError={isError || !task}>
      {task && (
        <>
          <PageSection type="breadcrumb">
            <Breadcrumb>
              <BreadcrumbItem>
                <Link to={Paths.tasks}>Tasks</Link>
              </BreadcrumbItem>
              <BreadcrumbItem isActive>Task {task.id}</BreadcrumbItem>
            </Breadcrumb>
          </PageSection>
          <PageSection isFilled>
            <Flex flexWrap={{ default: "nowrap" }}>
              <FlexItem flex={{ default: "flex_1" }}>
                <Content component="h1">{task.title}</Content>
                <TaskContextSidebar task={task} />
                {isTerminalOpen &&
                  task.workspace?.workspaceId &&
                  task.workspace?.id && (
                    <ThemedTerminal workspaceEntityId={task.workspace.id} />
                  )}
              </FlexItem>
              {task.workspace?.id && (
                <FlexItem>
                  <TaskActionBar
                    workspaceId={task.workspace?.id as number}
                    onGitDiffClick={() => setIsGitDrawerOpen((prev) => !prev)}
                    isGitDiffActive={isGitDrawerOpen}
                    onTerminalClick={() => setIsTerminalOpen((prev) => !prev)}
                    isTerminalActive={isTerminalOpen}
                  />
                </FlexItem>
              )}
            </Flex>
          </PageSection>

          {task.workspace?.id && (
            <PageDrawerContent
              isExpanded={isGitDrawerOpen}
              onCloseClick={() => setIsGitDrawerOpen(false)}
              header={<Title headingLevel="h2">Git Changes</Title>}
              pageKey="git-diff"
            >
              <GitDiffDrawer workspaceId={task.workspace.id} />
            </PageDrawerContent>
          )}
        </>
      )}
    </AsyncStateRenderer>
  );
};
