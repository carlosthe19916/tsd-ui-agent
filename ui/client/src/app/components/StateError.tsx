import type React from "react";

import {
  EmptyState,
  EmptyStateBody,
  EmptyStateVariant,
} from "@patternfly/react-core";
import ExclamationCircleIcon from "@patternfly/react-icons/dist/esm/icons/exclamation-circle-icon";

export interface StateErrorProps {
  title?: string;
  description?: string;
}

export const StateError: React.FC<StateErrorProps> = ({
  title = "Unable to load data",
  description = "There was an error loading data. Check your connection and try refreshing the page.",
}) => (
  <EmptyState
    titleText={title}
    headingLevel="h4"
    icon={ExclamationCircleIcon}
    variant={EmptyStateVariant.sm}
    status="danger"
  >
    <EmptyStateBody>{description}</EmptyStateBody>
  </EmptyState>
);
