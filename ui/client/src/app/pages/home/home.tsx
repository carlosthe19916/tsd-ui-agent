import type React from "react";

import { Content, PageSection, Title } from "@patternfly/react-core";

export const Home: React.FC = () => {
  return (
    <PageSection>
      <Title headingLevel="h1" size="2xl">
        Hello World
      </Title>
      <Content>
        <p>Welcome to TSD UI Agent.</p>
      </Content>
    </PageSection>
  );
};
