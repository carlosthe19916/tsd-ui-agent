import type {ReactNode} from 'react';
import clsx from 'clsx';
import Heading from '@theme/Heading';
import styles from './styles.module.css';

type FeatureItem = {
  title: string;
  description: ReactNode;
};

const FeatureList: FeatureItem[] = [
  {
    title: 'Import from GitHub & Jira',
    description: (
      <>
        Connect your existing project trackers and automatically sync issues
        as tasks ready for AI-assisted implementation.
      </>
    ),
  },
  {
    title: 'AI-Powered Planning',
    description: (
      <>
        A coding agent (Claude CLI or OpenCode) analyzes your codebase and
        generates step-by-step implementation plans for each task.
      </>
    ),
  },
  {
    title: 'Automated Code Changes',
    description: (
      <>
        Plans are executed in isolated git worktrees and submitted as pull
        requests, keeping your main branch safe.
      </>
    ),
  },
];

function Feature({title, description}: FeatureItem) {
  return (
    <div className={clsx('col col--4')}>
      <div className="text--center padding-horiz--md padding-vert--lg">
        <Heading as="h3">{title}</Heading>
        <p>{description}</p>
      </div>
    </div>
  );
}

export default function HomepageFeatures(): ReactNode {
  return (
    <section className={styles.features}>
      <div className="container">
        <div className="row">
          {FeatureList.map((props, idx) => (
            <Feature key={idx} {...props} />
          ))}
        </div>
      </div>
    </section>
  );
}
