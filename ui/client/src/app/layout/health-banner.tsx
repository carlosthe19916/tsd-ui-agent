import { Alert, List, ListItem } from "@patternfly/react-core";

import { useFetchHealthReady } from "@app/queries/health";
import type { HealthCheck } from "@app/api/health-api";

import styles from "./health-banner.module.css";

export const HealthBanner: React.FC = () => {
  const { data } = useFetchHealthReady();

  if (!data || data.status === "UP") {
    return null;
  }

  const failingChecks = data.checks.filter((c) => c.status !== "UP");

  return (
    <div className={styles.banner}>
      <Alert variant="danger" isInline title="Required tools are not available">
        <List>
          {failingChecks.map((check) => (
            <ListItem key={check.name}>
              <strong>{check.name}</strong>: {formatCheckData(check)}
            </ListItem>
          ))}
        </List>
      </Alert>
    </div>
  );
};

function formatCheckData(check: HealthCheck): string {
  if (!check.data) {
    return "unavailable";
  }
  const reasons = Object.entries(check.data)
    .filter(([key]) => key !== "command")
    .map(([, value]) => value);
  return reasons.join(", ") || "unavailable";
}
