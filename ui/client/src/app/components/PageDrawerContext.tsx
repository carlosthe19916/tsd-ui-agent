import React from "react";
import {
  Drawer,
  DrawerActions,
  DrawerCloseButton,
  DrawerContent,
  DrawerContentBody,
  DrawerHead,
  DrawerPanelBody,
  DrawerPanelContent,
  type DrawerPanelContentProps,
} from "@patternfly/react-core";
import pageStyles from "@patternfly/react-styles/css/components/Page/page";

const usePageDrawerState = () => {
  const [isDrawerExpanded, setIsDrawerExpanded] = React.useState(false);
  const [drawerPanelContent, setDrawerPanelContent] =
    React.useState<React.ReactNode>(null);
  const [drawerPanelContentProps, setDrawerPanelContentProps] = React.useState<
    Partial<DrawerPanelContentProps>
  >({});
  const [drawerPageKey, setDrawerPageKey] = React.useState<string>("");
  const drawerFocusRef = React.useRef(document.createElement("span"));
  return {
    isDrawerExpanded,
    setIsDrawerExpanded,
    drawerPanelContent,
    setDrawerPanelContent,
    drawerPanelContentProps,
    setDrawerPanelContentProps,
    drawerPageKey,
    setDrawerPageKey,
    drawerFocusRef: drawerFocusRef as typeof drawerFocusRef | null,
  };
};

type PageDrawerState = ReturnType<typeof usePageDrawerState>;

const PageDrawerContext = React.createContext<PageDrawerState>({
  isDrawerExpanded: false,
  setIsDrawerExpanded: () => {},
  drawerPanelContent: null,
  setDrawerPanelContent: () => {},
  drawerPanelContentProps: {},
  setDrawerPanelContentProps: () => {},
  drawerPageKey: "",
  setDrawerPageKey: () => {},
  drawerFocusRef: null,
});

interface PageContentWithDrawerProviderProps {
  children: React.ReactNode;
}

export const PageContentWithDrawerProvider: React.FC<
  PageContentWithDrawerProviderProps
> = ({ children }) => {
  const pageDrawerState = usePageDrawerState();
  const {
    isDrawerExpanded,
    drawerFocusRef,
    drawerPanelContent,
    drawerPanelContentProps,
    drawerPageKey,
  } = pageDrawerState;
  return (
    <PageDrawerContext.Provider value={pageDrawerState}>
      <div className={pageStyles.pageDrawer}>
        <Drawer
          isExpanded={isDrawerExpanded}
          onExpand={() => drawerFocusRef?.current?.focus()}
          position="right"
        >
          <DrawerContent
            panelContent={
              <DrawerPanelContent
                isResizable
                id="page-drawer-content"
                defaultSize="500px"
                minSize="150px"
                key={drawerPageKey}
                {...drawerPanelContentProps}
              >
                {drawerPanelContent}
              </DrawerPanelContent>
            }
          >
            <DrawerContentBody>{children}</DrawerContentBody>
          </DrawerContent>
        </Drawer>
      </div>
    </PageDrawerContext.Provider>
  );
};

export interface PageDrawerContentProps {
  isExpanded: boolean;
  onCloseClick: () => void;
  header?: React.ReactNode;
  children: React.ReactNode;
  drawerPanelContentProps?: Partial<DrawerPanelContentProps>;
  focusKey?: string | number;
  pageKey: string;
}

export const PageDrawerContent: React.FC<PageDrawerContentProps> = ({
  isExpanded,
  onCloseClick,
  header = null,
  children,
  drawerPanelContentProps,
  pageKey: localPageKeyProp,
}) => {
  const {
    setIsDrawerExpanded,
    drawerFocusRef,
    setDrawerPanelContent,
    setDrawerPanelContentProps,
    setDrawerPageKey,
  } = React.useContext(PageDrawerContext);

  React.useEffect(() => {
    setIsDrawerExpanded(isExpanded);
    return () => {
      setIsDrawerExpanded(false);
      setDrawerPanelContent(null);
    };
  }, [isExpanded, setDrawerPanelContent, setIsDrawerExpanded]);

  React.useEffect(() => {
    setDrawerPageKey(localPageKeyProp);
    return () => {
      setDrawerPageKey("");
    };
  }, [localPageKeyProp, setDrawerPageKey]);

  React.useEffect(() => {
    setDrawerPanelContentProps(drawerPanelContentProps || {});
  }, [drawerPanelContentProps, setDrawerPanelContentProps]);

  React.useEffect(() => {
    const drawerHead = header === null ? children : header;
    const drawerPanelBody = header === null ? null : children;

    setDrawerPanelContent(
      <>
        <DrawerHead>
          <span tabIndex={isExpanded ? 0 : -1} ref={drawerFocusRef}>
            {drawerHead}
          </span>
          <DrawerActions>
            <DrawerCloseButton onClick={onCloseClick} />
          </DrawerActions>
        </DrawerHead>
        <DrawerPanelBody>{drawerPanelBody}</DrawerPanelBody>
      </>,
    );
  }, [
    children,
    drawerFocusRef,
    header,
    isExpanded,
    onCloseClick,
    setDrawerPanelContent,
  ]);

  return null;
};
