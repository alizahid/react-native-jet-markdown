/**
 * biome-ignore-all lint/correctness/noNodejsModules: Expo config plugins are
 * CommonJS Node modules by contract.
 */
const { withPodfile } = require("expo/config-plugins");

// Adds the HeaderCollisionCanary pod to the prebuild-generated Podfile. The
// canary fails to compile if JetMarkdown's headers ever re-enter the Pods
// project and shadow other pods' md4c.h / Parser.h through Xcode's
// basename header maps (the 0.2.0-beta.0 consumer breakage).
module.exports = function withHeaderCollisionCanary(config) {
  return withPodfile(config, (podfile) => {
    const anchor = "config = use_native_modules!(config_command)";
    if (!podfile.modResults.contents.includes("HeaderCollisionCanary")) {
      podfile.modResults.contents = podfile.modResults.contents.replace(
        anchor,
        `${anchor}\n\n  # Regression canary: fails to compile if JetMarkdown's headers ever\n  # re-enter the Pods project and shadow other pods' md4c.h / Parser.h via\n  # Xcode's basename header maps.\n  pod 'HeaderCollisionCanary', :path => '../header-collision-canary'`
      );
    }
    return podfile;
  });
};
