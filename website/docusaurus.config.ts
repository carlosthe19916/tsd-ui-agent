import {themes as prismThemes} from 'prism-react-renderer';
import type {Config} from '@docusaurus/types';
import type * as Preset from '@docusaurus/preset-classic';

const config: Config = {
  title: 'TSD UI Agent',
  tagline: 'AI-assisted task management and code generation',
  favicon: 'img/favicon.ico',

  future: {
    v4: true,
  },

  url: 'https://carlosthe19916.github.io',
  baseUrl: '/tsd-ui-agent/',

  organizationName: 'carlosthe19916',
  projectName: 'tsd-ui-agent',

  onBrokenLinks: 'throw',

  i18n: {
    defaultLocale: 'en',
    locales: ['en'],
  },

  presets: [
    [
      'classic',
      {
        docs: {
          sidebarPath: './sidebars.ts',
        },
        blog: false,
        theme: {
          customCss: './src/css/custom.css',
        },
      } satisfies Preset.Options,
    ],
  ],

  themeConfig: {
    colorMode: {
      respectPrefersColorScheme: true,
    },
    navbar: {
      title: 'TSD UI Agent',
      items: [
        {
          type: 'docSidebar',
          sidebarId: 'tutorialSidebar',
          position: 'left',
          label: 'Docs',
        },
        {
          href: 'https://github.com/carlosthe19916/tsd-ui-agent',
          label: 'GitHub',
          position: 'right',
        },
      ],
    },
    footer: {
      style: 'dark',
      links: [
        {
          title: 'Docs',
          items: [
            {
              label: 'Introduction',
              to: '/docs',
            },
            {
              label: 'Architecture',
              to: '/docs/architecture',
            },
            {
              label: 'Configuration',
              to: '/docs/configuration',
            },
          ],
        },
      ],
      copyright: `Copyright \u00a9 ${new Date().getFullYear()} TSD UI Agent. Built with Docusaurus.`,
    },
    prism: {
      theme: prismThemes.github,
      darkTheme: prismThemes.dracula,
      additionalLanguages: ['java', 'properties', 'bash'],
    },
  } satisfies Preset.ThemeConfig,
};

export default config;
