import type React from "react";

import { AsyncStateRenderer } from "@app/components/AsyncStateRenderer";

export interface IConditionalDataListBodyProps {
  isLoading?: boolean;
  isError?: boolean;
  isNoData?: boolean;
  errorEmptyState?: React.ReactNode;
  noDataEmptyState?: React.ReactNode;
  children: React.ReactNode;
}

export const ConditionalDataListBody: React.FC<
  IConditionalDataListBodyProps
> = ({
  isLoading = false,
  isError = false,
  isNoData = false,
  errorEmptyState,
  noDataEmptyState,
  children,
}) => (
  <AsyncStateRenderer
    isLoading={isLoading}
    isError={isError}
    isEmpty={isNoData}
    errorContent={errorEmptyState}
    emptyContent={noDataEmptyState}
  >
    {children}
  </AsyncStateRenderer>
);
