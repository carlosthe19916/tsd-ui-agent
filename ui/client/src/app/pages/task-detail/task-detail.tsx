import type React from "react";
import { useState } from "react";

import { useParams, Link } from "react-router-dom";

import {
  Breadcrumb,
  BreadcrumbItem,
  Content,
  Flex,
  FlexItem,
  Grid,
  GridItem,
  PageSection,
  Spinner,
  Title,
} from "@patternfly/react-core";

import { Paths } from "@app/Routes";
import { PageDrawerContent } from "@app/components/PageDrawerContext";
import { useFetchTask } from "@app/queries/tasks";

import { GitDiffDrawer } from "./components/git-diff-drawer";
import { TaskActionBar } from "./components/task-action-bar";
import { TaskChatPanel } from "./components/task-chat-panel";
import { TaskContextSidebar } from "./components/task-context-sidebar";

export const TaskDetail: React.FC = () => {
  const { taskId } = useParams<{ taskId: string }>();
  const taskIdNum = Number(taskId);

  const { data: task, isLoading, isError } = useFetchTask(taskIdNum);
  const [isGitDrawerOpen, setIsGitDrawerOpen] = useState(false);

  if (isLoading) {
    return (
      <PageSection>
        <Spinner />
      </PageSection>
    );
  }

  if (isError || !task) {
    return (
      <PageSection>
        <Title headingLevel="h1">Task not found</Title>
      </PageSection>
    );
  }

  return (
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
        <Flex flexWrap={{ default: "nowrap" }} style={{ height: "100%" }}>
          <FlexItem flex={{ default: "flex_1" }} style={{ minWidth: 0 }}>
            <Grid hasGutter>
              <GridItem md={6}>
                <Content component="h1">{task.title}</Content>
                <TaskContextSidebar task={task} />
              </GridItem>
              <GridItem md={6}>
                <TaskChatPanel
                  taskId={task.id}
                  hasWorkspace={!!task.workspace?.workspaceId}
                />
              </GridItem>
            </Grid>
          </FlexItem>
          {task.workspace?.id && (
            <FlexItem>
              <TaskActionBar
                onGitDiffClick={() => setIsGitDrawerOpen((prev) => !prev)}
                isGitDiffActive={isGitDrawerOpen}
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
  );
};
