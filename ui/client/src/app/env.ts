import { buildTsdEnv, decodeEnv } from "@tsd-ui/common";

export const ENV = buildTsdEnv(decodeEnv(window._env));

export default ENV;
