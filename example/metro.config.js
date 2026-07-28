/** biome-ignore-all lint/correctness/noGlobalDirnameFilename: CommonJS config — import.meta syntax flips Node module detection and breaks require() */
const path = require("node:path");
const { getDefaultConfig } = require("expo/metro-config");
const { withMetroConfig } = require("react-native-monorepo-config");

const root = path.resolve(__dirname, "..");

/**
 * Metro configuration
 * https://facebook.github.io/metro/docs/configuration
 *
 * @type {import('metro-config').MetroConfig}
 */
const config = withMetroConfig(getDefaultConfig(__dirname), {
  root,
  dirname: __dirname,
  conditions: ["react-native-jet-markdown-source"],
});

module.exports = config;
