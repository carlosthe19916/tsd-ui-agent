import type React from "react";
import { use } from "react";

import { CodeEditor } from "@patternfly/react-code-editor";

import { ThemeContext } from "@app/components/ThemeContext";

type ThemedCodeEditorProps = Omit<
  React.ComponentProps<typeof CodeEditor>,
  "isDarkTheme"
>;

export const ThemedCodeEditor: React.FC<ThemedCodeEditorProps> = (props) => {
  const { isDark } = use(ThemeContext);
  return <CodeEditor isDarkTheme={isDark} {...props} />;
};
