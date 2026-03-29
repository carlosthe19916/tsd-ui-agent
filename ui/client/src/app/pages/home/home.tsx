import type React from "react";

import {
  Card,
  CardBody,
  CardTitle,
  Content,
  DataList,
  DataListCell,
  DataListItem,
  DataListItemCells,
  DataListItemRow,
  EmptyState,
  EmptyStateBody,
  EmptyStateVariant,
  Flex,
  FlexItem,
  Gallery,
  GalleryItem,
  Icon,
  Label,
  PageSection,
  Spinner,
  Title,
} from "@patternfly/react-core";
import KeyIcon from "@patternfly/react-icons/dist/esm/icons/key-icon";
import CodeBranchIcon from "@patternfly/react-icons/dist/esm/icons/code-branch-icon";
import FolderOpenIcon from "@patternfly/react-icons/dist/esm/icons/folder-open-icon";
import TaskIcon from "@patternfly/react-icons/dist/esm/icons/task-icon";
import CubesIcon from "@patternfly/react-icons/dist/esm/icons/cubes-icon";
import { Link, useNavigate } from "react-router-dom";

import type { TaskDto } from "@app/api/models";
import { useFetchCredentials } from "@app/queries/credentials";
import { useFetchGits } from "@app/queries/gits";
import { useFetchProjects } from "@app/queries/projects";
import { useFetchTasks } from "@app/queries/tasks";

interface StatCardProps {
  title: string;
  count: number | undefined;
  isLoading: boolean;
  icon: React.ComponentType;
  onClick: () => void;
}

const StatCard: React.FC<StatCardProps> = ({
  title,
  count,
  isLoading,
  icon: IconComponent,
  onClick,
}) => (
  <GalleryItem>
    <Card isClickable isSelectable onClick={onClick}>
      <CardTitle>{title}</CardTitle>
      <CardBody>
        <Flex
          alignItems={{ default: "alignItemsCenter" }}
          gap={{ default: "gapMd" }}
        >
          <FlexItem>
            <Icon size="xl">
              <IconComponent />
            </Icon>
          </FlexItem>
          <FlexItem>
            {isLoading ? (
              <Spinner size="md" />
            ) : (
              <Title headingLevel="h3" size="3xl">
                {count ?? 0}
              </Title>
            )}
          </FlexItem>
        </Flex>
      </CardBody>
    </Card>
  </GalleryItem>
);

const getPlanStatusLabel = (task: TaskDto) => {
  const plan = task.plan;
  if (!plan) return <Label color="grey">No plan</Label>;
  if (plan.changeRequestUrl) return <Label color="green">PR created</Label>;
  if (plan.isChangeRequestInProgress)
    return <Label color="blue">Creating PR</Label>;
  if (plan.changeRequestError) return <Label color="red">PR error</Label>;
  if (plan.executionPlanCompletedAt)
    return <Label color="green">Executed</Label>;
  if (plan.isExecutionPlanInProgress)
    return <Label color="blue">Executing</Label>;
  if (plan.executionPlanError)
    return <Label color="red">Execution error</Label>;
  if (plan.isPlanGenerationInProgress)
    return <Label color="blue">Generating plan</Label>;
  if (plan.planGenerationError) return <Label color="red">Plan error</Label>;
  if (plan.plan) return <Label color="cyan">Plan ready</Label>;
  if (plan.isRequirementInProgress)
    return <Label color="blue">Analyzing</Label>;
  return <Label color="grey">Draft</Label>;
};

export const Home: React.FC = () => {
  const navigate = useNavigate();

  const { data: credentials, isLoading: isLoadingCredentials } =
    useFetchCredentials();
  const { data: gits, isLoading: isLoadingGits } = useFetchGits();
  const { data: projects, isLoading: isLoadingProjects } = useFetchProjects();
  const { data: tasksResult, isLoading: isLoadingTasks } = useFetchTasks({
    page: { pageNumber: 1, itemsPerPage: 1 },
  });

  const { data: workspaceTasks, isLoading: isLoadingWorkspaceTasks } =
    useFetchTasks({
      filters: [{ field: "hasWorkspace", value: "true" }],
      page: { pageNumber: 1, itemsPerPage: 20 },
      sort: { field: "updatedAt", direction: "desc" },
    });

  const activeWorkspaces = workspaceTasks?.data ?? [];

  return (
    <>
      <PageSection>
        <Title headingLevel="h1" size="2xl">
          Dashboard
        </Title>
        <Content>
          <p>
            Manage your software development tasks imported from GitHub and
            Jira, and orchestrate AI-assisted code changes.
          </p>
        </Content>
      </PageSection>

      <PageSection>
        <Gallery hasGutter minWidths={{ default: "200px" }}>
          <StatCard
            title="Credentials"
            count={credentials?.length}
            isLoading={isLoadingCredentials}
            icon={KeyIcon}
            onClick={() => navigate("/credentials")}
          />
          <StatCard
            title="Git Repositories"
            count={gits?.length}
            isLoading={isLoadingGits}
            icon={CodeBranchIcon}
            onClick={() => navigate("/gits")}
          />
          <StatCard
            title="Projects"
            count={projects?.length}
            isLoading={isLoadingProjects}
            icon={FolderOpenIcon}
            onClick={() => navigate("/projects")}
          />
          <StatCard
            title="Tasks"
            count={tasksResult?.meta.count}
            isLoading={isLoadingTasks}
            icon={TaskIcon}
            onClick={() => navigate("/tasks")}
          />
        </Gallery>
      </PageSection>

      <PageSection>
        <Card>
          <CardTitle>Active Workspaces</CardTitle>
          <CardBody>
            {isLoadingWorkspaceTasks ? (
              <Spinner size="lg" />
            ) : activeWorkspaces.length === 0 ? (
              <EmptyState
                titleText="No active workspaces"
                headingLevel="h4"
                icon={CubesIcon}
                variant={EmptyStateVariant.sm}
              >
                <EmptyStateBody>
                  Create a workspace from a task to start working on code
                  changes.
                </EmptyStateBody>
              </EmptyState>
            ) : (
              <DataList aria-label="Active workspaces" isCompact>
                {activeWorkspaces.map((task) => (
                  <DataListItem key={task.id}>
                    <DataListItemRow>
                      <DataListItemCells
                        dataListCells={[
                          <DataListCell key="title" width={3}>
                            <Link to={`/tasks/${task.id}`}>{task.title}</Link>
                          </DataListCell>,
                          <DataListCell key="project" width={1}>
                            {task.project?.name} ({task.type})
                          </DataListCell>,
                          <DataListCell key="status" width={1}>
                            {getPlanStatusLabel(task)}
                          </DataListCell>,
                        ]}
                      />
                    </DataListItemRow>
                  </DataListItem>
                ))}
              </DataList>
            )}
          </CardBody>
        </Card>
      </PageSection>
    </>
  );
};
