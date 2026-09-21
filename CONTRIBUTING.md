# Contributing

Contributions are always welcome, no matter how large or small!

We want this community to be friendly and respectful to each other. Please follow it in all your interactions with the project. Before contributing, please read the [code of conduct](./CODE_OF_CONDUCT.md).

## Development workflow

This project uses Bun workspaces. It contains the following packages:

- The library package in the root directory.
- An example app in the `example/` directory.

To get started with the project, make sure you have the correct version of [Node.js](https://nodejs.org/) installed. See the [`.nvmrc`](./.nvmrc) file for the version used in this project.

Run `bun install` in the root directory to install the required dependencies for each package:

```sh
bun install
```

Use the Bun version declared in `package.json` and keep `bun.lock` in sync.

The [example app](/example/) demonstrates usage of the library. You need to run it to test any changes you make.

It is configured to use the local version of the library, so any changes you make to the library's source code will be reflected in the example app. Changes to the library's JavaScript code will be reflected in the example app without a rebuild, but native code changes will require a rebuild of the example app.

If you want to use Android Studio or Xcode to edit the native code, you can open the `example/android` or `example/ios` directories respectively in those editors. To edit the Objective-C or Swift files, open `example/ios/JetMarkdownExample.xcworkspace` in Xcode and find the source files at `Pods > Development Pods > react-native-jet-markdown`.

To edit the Java or Kotlin files, open `example/android` in Android studio and find the source files at `react-native-jet-markdown` under `Android`.

You can use various commands from the root directory to work with the project.

To start the packager:

```sh
bun run example start
```

To run the example app on Android:

```sh
bun run example android
```

To run the example app on iOS:

```sh
bun run example ios
```

To confirm that the app is running with the new architecture, you can check the Metro logs for a message like this:

```sh
Running "JetMarkdownExample" with {"fabric":true,"initialProps":{"concurrentRoot":true},"rootTag":1}
```

Note the `"fabric":true` and `"concurrentRoot":true` properties.

To run the example app on Web:

```sh
bun run example web
```

Make sure your code passes TypeScript:

```sh
bun run typecheck
```

### Publishing to npm

We use [release-it](https://github.com/release-it/release-it) to make it easier to publish new versions. It handles common tasks like bumping version based on semver, creating tags and releases etc.

To publish new versions, run the following:

```sh
bun run release 1.1.0
```

Update `CHANGELOG.md` before releasing and commit the preparation changes. Leave
`package.json` at the current version: the release command bumps it, creates the
`v<version>` tag, pushes, publishes to npm, and creates the GitHub release.
GitHub release notes come from the first version section of `CHANGELOG.md`, so
keep the newest release at the top, including when promoting a beta to stable.

For 1.1.0, release `react-native-jet-video` 1.1.0 first, then
`react-native-jet-markdown` 1.1.0, so the documented optional integration is
available when markdown users upgrade. Both repositories use the command above.

### Scripts

The `package.json` file contains various scripts for common tasks:

- `bun install`: setup project by installing dependencies.
- `bun run typecheck`: type-check files with TypeScript.
- `bun run example start`: start the Metro server for the example app.
- `bun run example android`: run the example app on Android.
- `bun run example ios`: run the example app on iOS.
- `bun run example web`: run the example app on Web.
- `bun run example build:web`: build the example app for Web.

### Sending a pull request

> **Working on your first pull request?** You can learn how from this _free_ series: [How to Contribute to an Open Source Project on GitHub](https://app.egghead.io/playlists/how-to-contribute-to-an-open-source-project-on-github).

When you're sending a pull request:

- Prefer small pull requests focused on one change.
- Verify that linters and tests are passing.
- Review the documentation to make sure it looks good.
- Follow the pull request template when opening a pull request.
- For pull requests that change the API or implementation, discuss with maintainers first by opening an issue.
