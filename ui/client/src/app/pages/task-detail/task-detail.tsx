import type React from "react";

import { useParams, Link } from "react-router-dom";

import {
  Breadcrumb,
  BreadcrumbItem,
  Content,
  Flex,
  FlexItem,
  Grid,
  GridItem,
  Label,
  PageSection,
  Spinner,
  Split,
  SplitItem,
  Title,
} from "@patternfly/react-core";

import { Paths } from "@app/Routes";
import { useFetchTask } from "@app/queries/tasks";

import { TaskChatPanel } from "./components/task-chat-panel";
import { TaskContextSidebar } from "./components/task-context-sidebar";

export const TaskDetail: React.FC = () => {
  const { taskId } = useParams<{ taskId: string }>();
  const taskIdNum = Number(taskId);

  const { data: task, isLoading, isError } = useFetchTask(taskIdNum);

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
      </PageSection>
    </>
  );
};
