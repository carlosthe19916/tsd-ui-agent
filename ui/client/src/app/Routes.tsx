import { createBrowserRouter } from "react-router-dom";

import App from "./App";
import { Home } from "./pages/home/home";
import { NotFound } from "./pages/not-found/not-found";
import { CredentialList } from "./pages/credential-list";
import { GitList } from "./pages/git-list";
import { ProjectList } from "./pages/project-list";
import { TaskList } from "./pages/task-list";

export const Paths = {
  home: "/",
  projects: "/projects",
  credentials: "/credentials",
  gits: "/gits",
  tasks: "/tasks",
} as const;

export const AppRoutes = createBrowserRouter(
  [
    {
      path: "/",
      element: <App />,
      children: [
        {
          path: Paths.home,
          element: <Home />,
        },
        {
          path: Paths.projects,
          element: <ProjectList />,
        },
        {
          path: Paths.credentials,
          element: <CredentialList />,
        },
        {
          path: Paths.gits,
          element: <GitList />,
        },
        {
          path: Paths.tasks,
          element: <TaskList />,
        },
        {
          path: "*",
          element: <NotFound />,
        },
      ],
    },
  ],
  {
    basename: import.meta.env.BASE_URL,
  },
);
