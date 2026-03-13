import { Bullseye } from "@patternfly/react-core";

import { NotFoundEmptyState } from "@app/components/NotFoundEmptyState";

export const NotFound: React.FC = () => {
  return (
    <Bullseye>
      <NotFoundEmptyState />
    </Bullseye>
  );
};
