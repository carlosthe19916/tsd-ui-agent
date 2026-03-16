import React from "react";

import { yupResolver } from "@hookform/resolvers/yup";
import { useForm } from "react-hook-form";
import ReactMarkdown from "react-markdown";
import * as yup from "yup";

import { CodeEditor, Language } from "@patternfly/react-code-editor";
import {
  Button,
  Content,
  Drawer,
  DrawerContent,
  DrawerContentBody,
  DrawerPanelContent,
  Grid,
  GridItem,
  Panel,
  PanelMain,
  PanelMainBody,
  ToggleGroup,
  ToggleGroupItem,
  Toolbar,
  ToolbarContent,
  ToolbarItem,
} from "@patternfly/react-core";
import CodeIcon from "@patternfly/react-icons/dist/esm/icons/code-icon";
import ColumnsIcon from "@patternfly/react-icons/dist/esm/icons/columns-icon";
import EyeIcon from "@patternfly/react-icons/dist/esm/icons/eye-icon";
import RobotIcon from "@patternfly/react-icons/dist/esm/icons/robot-icon";

import { ThemeContext } from "@app/components/ThemeContext";
import { useFormChangeHandler } from "@app/hooks/useFormChangeHandler";
import { RequirementChatbot } from "./requirement-chatbot";

interface ExecutionPlanValues {
  executionPlan: string;
}

export interface ExecutionPlanState extends ExecutionPlanValues {
  isValid: boolean;
}

const schema = yup.object({
  executionPlan: yup.string().defined().default(""),
});

type ViewMode = "editor" | "preview" | "split";

interface ExecutionPlanStepProps {
  taskId: number;
  initialState: ExecutionPlanState;
  onStateChanged: (state: ExecutionPlanState) => void;
}

export const ExecutionPlanStep: React.FC<ExecutionPlanStepProps> = ({
  taskId,
  initialState,
  onStateChanged,
}) => {
  const { isDark } = React.useContext(ThemeContext);
  const [viewMode, setViewMode] = React.useState<ViewMode>("editor");
  const [isChatbotOpen, setIsChatbotOpen] = React.useState(false);

  const form = useForm<ExecutionPlanValues>({
    resolver: yupResolver(schema),
    mode: "all",
    defaultValues: { executionPlan: initialState.executionPlan },
  });

  useFormChangeHandler({ form, onStateChanged });

  const executionPlan = form.watch("executionPlan");

  return (
    <div style={{ height: "100%", display: "flex", flexDirection: "column" }}>
      <Drawer isExpanded={isChatbotOpen} isInline>
        <DrawerContent
          panelContent={
            <DrawerPanelContent isResizable defaultSize="400px" minSize="250px">
              <RequirementChatbot taskId={taskId} />
            </DrawerPanelContent>
          }
        >
          <DrawerContentBody
            style={isChatbotOpen ? { paddingRight: 10 } : undefined}
          >
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
                <ToolbarItem align={{ default: "alignEnd" }}>
                  <Button
                    variant={isChatbotOpen ? "primary" : "secondary"}
                    icon={<RobotIcon />}
                    onClick={() => setIsChatbotOpen(!isChatbotOpen)}
                  >
                    Chat Bot
                  </Button>
                </ToolbarItem>
              </ToolbarContent>
            </Toolbar>
            <Grid hasGutter>
              {viewMode !== "preview" && (
                <GridItem span={viewMode === "split" ? 6 : 12}>
                  <CodeEditor
                    isDarkTheme={isDark}
                    language={Language.markdown}
                    code={executionPlan}
                    onCodeChange={(value) =>
                      form.setValue("executionPlan", value, {
                        shouldValidate: true,
                        shouldDirty: true,
                      })
                    }
                    height="65vh"
                    isLineNumbersVisible
                    isHeaderPlain
                  />
                </GridItem>
              )}
              {viewMode !== "editor" && (
                <GridItem span={viewMode === "split" ? 6 : 12}>
                  <Panel variant="bordered" isScrollable>
                    <PanelMain tabIndex={0} style={{ minHeight: "65.5vh" }}>
                      <PanelMainBody>
                        <Content>
                          <ReactMarkdown>{executionPlan}</ReactMarkdown>
                        </Content>
                      </PanelMainBody>
                    </PanelMain>
                  </Panel>
                </GridItem>
              )}
            </Grid>
          </DrawerContentBody>
        </DrawerContent>
      </Drawer>
    </div>
  );
};
