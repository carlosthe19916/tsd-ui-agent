import { createBrowserRouter } from "react-router-dom";

import App from "./App";
import { Home } from "./pages/home/home";
import { NotFound } from "./pages/not-found/not-found";
import { CredentialList } from "./pages/credential-list";
import { ProjectList } from "./pages/project-list";

export const Paths = {
  home: "/",
  projects: "/projects",
  credentials: "/credentials",
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
