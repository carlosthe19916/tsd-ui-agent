import React from "react";

import { yupResolver } from "@hookform/resolvers/yup";
import { useForm } from "react-hook-form";
import ReactMarkdown from "react-markdown";
import * as yup from "yup";

import { Language } from "@patternfly/react-code-editor";
import {
  Content,
  Flex,
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

import { ThemedCodeEditor } from "@app/components/ThemedCodeEditor";
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
  const [viewMode, setViewMode] = React.useState<ViewMode>("editor");

  const form = useForm<RequirementValues>({
    resolver: yupResolver(schema),
    mode: "all",
    defaultValues: { requirement: initialState.requirement },
  });

  useFormChangeHandler({ form, onStateChanged });

  const requirement = form.watch("requirement");

  return (
    <Flex direction={{ default: "column" }} flexWrap={{ default: "nowrap" }}>
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
            <ThemedCodeEditor
              language={Language.markdown}
              code={requirement}
              onCodeChange={(value) =>
                form.setValue("requirement", value, {
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
              <PanelMain tabIndex={0}>
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
    </Flex>
  );
};
