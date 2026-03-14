import type React from "react";

import {
  Brand,
  Masthead,
  MastheadBrand,
  MastheadContent,
  MastheadLogo,
  MastheadMain,
  MastheadToggle,
  PageToggleButton,
  Split,
  SplitItem,
  Title,
  Toolbar,
  ToolbarContent,
  ToolbarGroup,
  ToolbarItem,
} from "@patternfly/react-core";

import BarsIcon from "@patternfly/react-icons/dist/js/icons/bars-icon";

import { ThemeSelector } from "@app/components/ThemeSelector";
import useBranding from "@app/hooks/useBranding";

export const HeaderApp: React.FC = () => {
  const {
    masthead: { leftBrand, leftTitle },
  } = useBranding();

  return (
    <Masthead>
      <MastheadMain>
        <MastheadToggle>
          <PageToggleButton variant="plain" aria-label="Global navigation">
            <BarsIcon />
          </PageToggleButton>
        </MastheadToggle>
        <MastheadBrand>
          <MastheadLogo>
            <Split>
              <SplitItem>
                {leftBrand ? (
                  <Brand
                    src={leftBrand.src}
                    alt={leftBrand.alt}
                    heights={{ default: leftBrand.height }}
                  />
                ) : null}
              </SplitItem>
              <SplitItem isFilled>
                {leftTitle ? (
                  <Title
                    className="logo-pointer"
                    headingLevel={leftTitle?.heading ?? "h1"}
                    size={leftTitle?.size ?? "2xl"}
                  >
                    {leftTitle.text}
                  </Title>
                ) : null}
              </SplitItem>
            </Split>
          </MastheadLogo>
        </MastheadBrand>
      </MastheadMain>
      <MastheadContent>
        <Toolbar id="toolbar" isFullHeight isStatic>
          <ToolbarContent>
            <ToolbarGroup
              variant="action-group-plain"
              align={{ default: "alignEnd" }}
              gap={{ default: "gapNone", md: "gapMd" }}
            >
              <ToolbarItem>
                <ThemeSelector />
              </ToolbarItem>
            </ToolbarGroup>
          </ToolbarContent>
        </Toolbar>
      </MastheadContent>
    </Masthead>
  );
};
