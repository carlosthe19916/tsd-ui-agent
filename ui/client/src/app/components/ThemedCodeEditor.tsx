import React from "react";

import { CodeEditor } from "@patternfly/react-code-editor";

import { ThemeContext } from "@app/components/ThemeContext";

type ThemedCodeEditorProps = Omit<
  React.ComponentProps<typeof CodeEditor>,
  "isDarkTheme"
>;

export const ThemedCodeEditor: React.FC<ThemedCodeEditorProps> = (props) => {
  const { isDark } = React.useContext(ThemeContext);
  return <CodeEditor isDarkTheme={isDark} {...props} />;
};
