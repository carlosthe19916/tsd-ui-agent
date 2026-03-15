import React from "react";

import {
  Content,
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
import { CodeEditor, Language } from "@patternfly/react-code-editor";
import CodeIcon from "@patternfly/react-icons/dist/esm/icons/code-icon";
import ColumnsIcon from "@patternfly/react-icons/dist/esm/icons/columns-icon";
import EyeIcon from "@patternfly/react-icons/dist/esm/icons/eye-icon";
import ReactMarkdown from "react-markdown";
import { useForm } from "react-hook-form";
import { yupResolver } from "@hookform/resolvers/yup";
import * as yup from "yup";

import { ThemeContext } from "@app/components/ThemeContext";
import { useFormChangeHandler } from "@app/hooks/useFormChangeHandler";

interface RequirementValues {
  requirement: string;
}

export interface RequirementState extends RequirementValues {
  isValid: boolean;
}

const schema = yup.object({
  requirement: yup.string().defined().default(""),
});

type ViewMode = "editor" | "preview" | "split";

interface RequirementStepProps {
  initialState: RequirementState;
  onStateChanged: (state: RequirementState) => void;
}

export const RequirementStep: React.FC<RequirementStepProps> = ({
  initialState,
  onStateChanged,
}) => {
  const { isDark } = React.useContext(ThemeContext);
  const [viewMode, setViewMode] = React.useState<ViewMode>("split");

  const form = useForm<RequirementValues>({
    resolver: yupResolver(schema),
    mode: "all",
    defaultValues: { requirement: initialState.requirement },
  });

  useFormChangeHandler({ form, onStateChanged });

  const requirement = form.watch("requirement");

  return (
    <>
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
              code={requirement}
              onCodeChange={(value) =>
                form.setValue("requirement", value, {
                  shouldValidate: true,
                  shouldDirty: true,
                })
              }
              height="60vh"
              isLineNumbersVisible
              isHeaderPlain
            />
          </GridItem>
        )}
        {viewMode !== "editor" && (
          <GridItem span={viewMode === "split" ? 6 : 12}>
            <Panel variant="bordered" isScrollable>
              <PanelMain tabIndex={0} style={{ minHeight: "61.5vh" }}>
                <PanelMainBody>
                  <Content>
                    <ReactMarkdown>{requirement}</ReactMarkdown>
                  </Content>
                </PanelMainBody>
              </PanelMain>
            </Panel>
          </GridItem>
        )}
      </Grid>
    </>
  );
};
