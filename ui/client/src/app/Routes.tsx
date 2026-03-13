import { createBrowserRouter } from "react-router-dom";

import App from "./App";
import { Home } from "./pages/home/home";
import { NotFound } from "./pages/not-found/not-found";

export const Paths = {
  home: "/",
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
