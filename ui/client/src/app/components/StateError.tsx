import type React from "react";

import { EmptyState, EmptyStateBody } from "@patternfly/react-core";

export const StateError: React.FC = () => (
  <EmptyState>
    <EmptyStateBody>An error occurred. Please try again.</EmptyStateBody>
  </EmptyState>
);
