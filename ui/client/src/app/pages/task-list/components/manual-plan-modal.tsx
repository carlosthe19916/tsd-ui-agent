import React from "react";

import {
  Button,
  ButtonVariant,
  Content,
  Grid,
  GridItem,
  Modal,
  ModalBody,
  ModalFooter,
  ModalHeader,
  Panel,
  PanelMain,
  PanelMainBody,
  ToggleGroup,
  ToggleGroupItem,
  Toolbar,
  ToolbarContent,
  ToolbarItem,
} from "@patternfly/react-core";
import { CodeEditor, Language } from "@patternfly/react-code-editor";
import CodeIcon from "@patternfly/react-icons/dist/esm/icons/code-icon";
import ColumnsIcon from "@patternfly/react-icons/dist/esm/icons/columns-icon";
import EyeIcon from "@patternfly/react-icons/dist/esm/icons/eye-icon";
import ReactMarkdown from "react-markdown";

import type { TaskDto } from "@app/api/models";
import {
  useCreateTaskPlanMutation,
  useUpdateTaskPlanMutation,
} from "@app/queries/tasks";
import { ThemeContext } from "@app/components/ThemeContext";

interface ManualPlanModalProps {
  task: TaskDto | null;
  isOpen: boolean;
  onClose: () => void;
}

export const ManualPlanModal: React.FC<ManualPlanModalProps> = ({
  task,
  isOpen,
  onClose,
}) => {
  if (!isOpen || !task) return null;

  return <ManualPlanModalContent task={task} onClose={onClose} />;
};

type ViewMode = "editor" | "preview" | "split";

const ManualPlanModalContent: React.FC<{
  task: TaskDto;
  onClose: () => void;
}> = ({ task, onClose }) => {
  const { isDark } = React.useContext(ThemeContext);

  const initialContent = task.plan?.content ?? "";
  const [content, setContent] = React.useState(initialContent);
  const [viewMode, setViewMode] = React.useState<ViewMode>("split");

  const createMutation = useCreateTaskPlanMutation(onClose);
  const updateMutation = useUpdateTaskPlanMutation(onClose);

  const isPending = createMutation.isPending || updateMutation.isPending;
  const isUnchanged = content === initialContent;

  const handleSave = () => {
    if (task.plan) {
      updateMutation.mutate({
        taskId: task.id,
        plan: { ...task.plan, content },
      });
    } else {
      createMutation.mutate({
        taskId: task.id,
        plan: { content, status: "IN_PROGRESS", type: "MANUAL" },
      });
    }
  };

  return (
    <Modal isOpen onClose={onClose} aria-label="Manual plan" width="100%">
      <ModalHeader title="Manual plan" />
      <ModalBody>
        <Toolbar>
          <ToolbarContent>
            <ToolbarItem>
              <ToggleGroup aria-label="View mode">
                <ToggleGroupItem
                  icon={<CodeIcon />}
                  text="Editor"
                  aria-label="Editor view"
                  isSelected={viewMode === "editor"}
                  onChange={() => setViewMode("editor")}
                />
                <ToggleGroupItem
                  icon={<ColumnsIcon />}
                  text="Split"
                  aria-label="Split view"
                  isSelected={viewMode === "split"}
                  onChange={() => setViewMode("split")}
                />
                <ToggleGroupItem
                  icon={<EyeIcon />}
                  text="Preview"
                  aria-label="Preview view"
                  isSelected={viewMode === "preview"}
                  onChange={() => setViewMode("preview")}
                />
              </ToggleGroup>
            </ToolbarItem>
          </ToolbarContent>
        </Toolbar>

        <Grid hasGutter>
          {viewMode !== "preview" && (
            <GridItem span={viewMode === "split" ? 6 : 12}>
              <CodeEditor
                isDarkTheme={isDark}
                language={Language.markdown}
                code={content}
                onCodeChange={setContent}
                height="70vh"
                isLineNumbersVisible
                isHeaderPlain
              />
            </GridItem>
          )}
          {viewMode !== "editor" && (
            <GridItem span={viewMode === "split" ? 6 : 12}>
              <Panel variant="bordered" isScrollable>
                <PanelMain tabIndex={0} style={{ minHeight: "71.5vh" }}>
                  <PanelMainBody>
                    <Content>
                      <ReactMarkdown>{content}</ReactMarkdown>
                    </Content>
                  </PanelMainBody>
                </PanelMain>
              </Panel>
            </GridItem>
          )}
        </Grid>
      </ModalBody>
      <ModalFooter>
        <Button
          variant={ButtonVariant.primary}
          isDisabled={isUnchanged || isPending}
          isLoading={isPending}
          onClick={handleSave}
        >
          Save
        </Button>
        <Button
          variant={ButtonVariant.link}
          isDisabled={isPending}
          onClick={onClose}
        >
          Cancel
        </Button>
      </ModalFooter>
    </Modal>
  );
};
