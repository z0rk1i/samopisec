/** @type {import('@bacons/apple-targets/app.plugin').ConfigFunction} */
module.exports = config => ({
  type: "widget",
  name: "SamopisecWidget",
  deploymentTarget: "17.0",
  frameworks: ["WidgetKit", "SwiftUI", "AppIntents"],
  entitlements: {
    "com.apple.security.application-groups": ["group.com.z0rk1.samopisec"],
  },
});
