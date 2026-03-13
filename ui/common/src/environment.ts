/** Define process.env to contain `TsdEnvType` */
declare global {
  // eslint-disable-next-line @typescript-eslint/no-namespace
  namespace NodeJS {
    interface ProcessEnv extends Partial<Readonly<TsdEnvType>> {}
  }
}

/**
 * The set of environment variables used by `@tsd-ui` packages.
 */
export type TsdEnvType = {
  NODE_ENV: "development" | "production" | "test";
  VERSION: string;

  /** The listen port for the UI's server */
  PORT?: string;

  /** Target URL for the UI server's `/api` proxy */
  TSD_API_URL?: string;

  /** Location of branding files (relative paths computed from the project source root) */
  BRANDING?: string;
};

/**
 * Keys in `TsdEnvType` that are only used on the server and therefore do not
 * need to be sent to the client.
 */
export const SERVER_ENV_KEYS = ["PORT", "TSD_API_URL", "BRANDING"];

/**
 * Create a `TsdEnvType` from a partial `TsdEnvType` with a set of default values.
 */
export const buildTsdEnv = ({
  NODE_ENV = "production",
  PORT,
  VERSION = "99.0.0",

  TSD_API_URL,

  BRANDING,
}: Partial<TsdEnvType> = {}): TsdEnvType => ({
  NODE_ENV,
  PORT,
  VERSION,

  TSD_API_URL,

  BRANDING,
});

/**
 * Default values for `TsdEnvType`.
 */
export const TSD_ENV_DEFAULTS = buildTsdEnv();

/**
 * Current `@tsd-ui` environment configurations from `process.env`.
 */
export const TSD_ENV = buildTsdEnv(process.env);
