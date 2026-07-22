/**
 * Note: When using the Node.JS APIs, the config file
 * doesn't apply. Instead, pass options directly to the APIs.
 *
 * All configuration options: https://remotion.dev/docs/config
 */

import { Config } from "@remotion/cli/config";
import { enableTailwind } from "@remotion/tailwind-v4";

Config.setVideoImageFormat("jpeg");
Config.setOverwriteOutput(true);
Config.overrideWebpackConfig(enableTailwind);

// Use the pre-installed chrome-headless-shell in this environment instead of
// downloading Remotion's own. (The full Chromium build has removed old
// headless mode, so point at the headless-shell binary.)
Config.setBrowserExecutable(
  "/opt/pw-browsers/chromium_headless_shell-1194/chrome-linux/headless_shell",
);

// Google Fonts are fetched by the render browser through the agent proxy, whose
// CA the headless browser doesn't trust — allow it to load them anyway.
Config.setChromiumIgnoreCertificateErrors(true);
