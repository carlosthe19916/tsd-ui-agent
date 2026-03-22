import type React from "react";

import { Page, SkipToContent } from "@patternfly/react-core";

import { PageContentWithDrawerProvider } from "@app/components/PageDrawerContext";
import { HeaderApp } from "./header";
import { SidebarApp } from "./sidebar";

interface DefaultLayoutProps {
  children?: React.ReactNode;
}

export const DefaultLayout: React.FC<DefaultLayoutProps> = ({ children }) => {
  const pageId = "main-content-page-layout-horizontal-nav";
  const PageSkipToContent = (
    <SkipToContent href={`#${pageId}`}>Skip to content</SkipToContent>
  );

  return (
    <Page
      masthead={<HeaderApp />}
      sidebar={<SidebarApp />}
      isManagedSidebar
      defaultManagedSidebarIsOpen
      skipToContent={PageSkipToContent}
      mainContainerId={pageId}
    >
      <PageContentWithDrawerProvider>{children}</PageContentWithDrawerProvider>
    </Page>
  );
};
