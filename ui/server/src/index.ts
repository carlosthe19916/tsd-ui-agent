/* global process */

import path from "node:path";
import { fileURLToPath } from "node:url";

import ejs from "ejs";
import express from "express";
import { createProxyMiddleware } from "http-proxy-middleware";
import { createHttpTerminator } from "http-terminator";

import {
  TSD_ENV,
  SERVER_ENV_KEYS,
  brandingStrings,
  encodeEnv,
} from "@tsd-ui/common";
import proxies from "./proxies";

const debugMode = process.env.DEBUG === "1";
debugMode && console.log("CONSOLE_ENV", TSD_ENV);

const __dirname = fileURLToPath(new URL(".", import.meta.url));
const pathToClientDist = path.join(__dirname, "../../client/dist");

const port = TSD_ENV.PORT ? parseInt(TSD_ENV.PORT, 10) : 8080;

const app = express();
app.set("x-powered-by", false);

// Setup proxy handling
app.use(createProxyMiddleware(proxies.api));

app.engine("ejs", ejs.renderFile);
app.use(express.json());
app.set("views", pathToClientDist);
app.use(express.static(pathToClientDist));

// Handle any request that hasn't already been handled by express.static or proxy
app.get("*splat", (_, res) => {
  if (TSD_ENV.NODE_ENV === "development") {
    res.send(`
      <style>pre { margin-left: 20px; }</style>
      You're running in development mode! The UI is served by vite on port 3000: <a href="http://localhost:3000">http://localhost:3000</a><br /><br />
      If you want to serve the UI via express to simulate production mode, run a full build with: <pre>npm run build</pre>
      and then run: <pre>npm run start</pre> and the UI will be served on port 8080.
    `);
  } else {
    res.render("index.html.ejs", {
      _env: encodeEnv(TSD_ENV, SERVER_ENV_KEYS),
      branding: brandingStrings,
    });
  }
});

// Start the server
const server = app.listen(port, (error) => {
  if (error) {
    throw error;
  }
  console.log(`Server listening on port::${port}`);
});

// Handle shutdown signals Ctrl-C (SIGINT) and default podman/docker stop (SIGTERM)
const httpTerminator = createHttpTerminator({ server });

const shutdown = async (signal) => {
  if (!server) {
    console.log(`${signal}, no server running.`);
    return;
  }

  console.log(`${signal} - Stopping server on port::${port}`);
  await httpTerminator.terminate();
  console.log(`${signal} - Stopped server on port::${port}`);
};

process.on("SIGINT", shutdown);
process.on("SIGTERM", shutdown);
