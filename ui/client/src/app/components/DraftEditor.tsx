import React from "react";

import { Button, Stack, StackItem } from "@patternfly/react-core";
import { Language } from "@patternfly/react-code-editor";

import { ThemedCodeEditor } from "@app/components/ThemedCodeEditor";

interface DraftEditorProps {
  serverValue: string;
  onSave: (value: string) => void;
  isSaving: boolean;
  language?: string;
  height?: string;
}

export const DraftEditor: React.FC<DraftEditorProps> = ({
  serverValue,
  onSave,
  isSaving,
  language = Language.markdown,
  height = "29vh",
}) => {
  const [draft, setDraft] = React.useState(serverValue);

  return (
    <Stack hasGutter>
      <StackItem>
        <ThemedCodeEditor
          language={language}
          code={draft}
          onCodeChange={setDraft}
          height={height}
          isLineNumbersVisible
        />
      </StackItem>
      <StackItem>
        <Button
          variant="primary"
          onClick={() => onSave(draft)}
          isLoading={isSaving}
          isDisabled={draft === serverValue}
        >
          Save
        </Button>
      </StackItem>
    </Stack>
  );
};
