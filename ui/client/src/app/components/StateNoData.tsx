import type React from "react";

import {
  EmptyState,
  EmptyStateBody,
  EmptyStateVariant,
} from "@patternfly/react-core";
import CubesIcon from "@patternfly/react-icons/dist/esm/icons/cubes-icon";

export interface StateNoDataProps {
  title?: string;
  description?: string;
}

export const StateNoData: React.FC<StateNoDataProps> = ({
  title = "No data available",
  description = "No data available to be shown here.",
}) => (
  <EmptyState
    titleText={title}
    headingLevel="h4"
    icon={CubesIcon}
    variant={EmptyStateVariant.sm}
  >
    <EmptyStateBody>{description}</EmptyStateBody>
  </EmptyState>
);
