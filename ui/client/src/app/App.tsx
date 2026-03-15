import type React from "react";
import { Outlet } from "react-router-dom";

import { DefaultLayout } from "./layout";
import { ThemeProvider } from "./components/ThemeContext";

import "@patternfly/patternfly/patternfly-addons.css";
import "@patternfly/patternfly/patternfly.css";
import "@patternfly/chatbot/dist/css/main.css";

const App: React.FC = () => {
  return (
    <ThemeProvider>
      <DefaultLayout>
        <Outlet />
      </DefaultLayout>
    </ThemeProvider>
  );
};

export default App;
