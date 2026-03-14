import type React from "react";

import { Label } from "@patternfly/react-core";
import {
  CheckCircleIcon,
  ExclamationCircleIcon,
  InProgressIcon,
} from "@patternfly/react-icons";

import type { SyncStatus as SyncStatusType } from "@app/api/models";

interface SyncStatusProps {
  status?: SyncStatusType;
}

export const SyncStatus: React.FC<SyncStatusProps> = ({ status }) => {
  switch (status) {
    case "NOT_SYNCHRONIZED":
      return <Label color="grey">Not synchronized</Label>;
    case "SYNCHRONIZATION_IN_PROGRESS":
      return (
        <Label color="blue" icon={<InProgressIcon />}>
          Synchronizing
        </Label>
      );
    case "SYNCHRONIZED":
      return (
        <Label color="green" icon={<CheckCircleIcon />}>
          Synchronized
        </Label>
      );
    case "SYNC_ERROR":
      return (
        <Label color="red" icon={<ExclamationCircleIcon />}>
          Sync error
        </Label>
      );
    default:
      return <Label color="grey">N/A</Label>;
  }
};
