import type React from "react";

import { Bullseye, Spinner } from "@patternfly/react-core";

import { StateError } from "@app/components/StateError";
import { StateNoData } from "@app/components/StateNoData";

export interface AsyncStateRendererProps {
  isLoading: boolean;
  isError: boolean;
  isEmpty?: boolean;
  children: React.ReactNode;
  loadingContent?: React.ReactNode;
  errorContent?: React.ReactNode;
  emptyContent?: React.ReactNode;
}

export const AsyncStateRenderer: React.FC<AsyncStateRendererProps> = ({
  isLoading,
  isError,
  isEmpty = false,
  children,
  loadingContent,
  errorContent,
  emptyContent,
}) => (
  <>
    {isLoading ? (
      <Bullseye>{loadingContent ?? <Spinner size="xl" />}</Bullseye>
    ) : isError ? (
      <Bullseye>{errorContent ?? <StateError />}</Bullseye>
    ) : isEmpty ? (
      <Bullseye>{emptyContent ?? <StateNoData />}</Bullseye>
    ) : (
      children
    )}
  </>
);
