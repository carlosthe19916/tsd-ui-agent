import type React from "react";

import {
    EmptyState,
    EmptyStateBody,
    EmptyStateVariant,
} from "@patternfly/react-core";
import CubesIcon from "@patternfly/react-icons/dist/esm/icons/cubes-icon";

export const StateNoData: React.FC = () => (
    <EmptyState
        titleText={"No data available"}
        headingLevel="h4"
        icon={CubesIcon}
        variant={EmptyStateVariant.sm}
    >
        <EmptyStateBody>{"No data available to be shown here."}</EmptyStateBody>
    </EmptyState>
);
