# Tacit

<p align="center">
    <img src="tacit.png" alt="Tacit logo" width="120" />
</p>

**Tacit** is a fork of **Tammy Messenger** with one clear goal: a modern, appealing Matrix experience.

> Quietly powerful. Smooth by default. Built for focus.

Tacit is based on [Trixnity Messenger](https://gitlab.com/connect2x/trixnity-messenger/trixnity-messenger), with an updated visual style, cleaner interaction patterns, and a UI-first product direction.

## Why Tacit?

- A modern, polished desktop-first Matrix experience.
- Faster, clearer room navigation with a contemporary chat layout.
- Matrix power under the hood, with UX tuned for daily use.
- A fork that focuses on delight, flow, and visual consistency.

## Status

- **Target platform:** Desktop.
- **Other platforms:** Android/Web/iOS are currently not actively tested in Tacit.
- **Maturity:** Alpha — expect rough edges, breaking changes, and frequent iteration.

## Original README below

The original Tammy README content follows below for reference.

## Run locally

Run Tacit from your IDE or command line:

### Desktop

`./gradlew run`

### Android

In Android Studio or IntelliJ, choose the Android configuration and run on an emulated or your physical device.

### Web

Please **note**: web is still experimental and is missing some features that are present in the other versions. We are
working on it.

`./gradlew webBrowserDevelopmentRun`

## Create release

1. Create a git commit `bump version` with the following changes:
    - `appPublishedVersion` (e. g. `1.2.3`) set to the version that is going to be published.
    - `appVersion` must be the same as `appPublishedVersion`.
    - Updated `CHANGELOG.md` containing a section about the new version.
    - Cancel triggered pipeline.
2. Create a version-tag of the form `v1.2.3`.
    - The version must be the same as `appPublishedVersion`.
    - This will trigger a pipeline creating all distributions, uploading them into package registry and linking them in
      a newly created GitLab release.
3. Create a git commit with `[skip-ci]` in the commit message and the following changes:
    - `appPublishedVersion` (e. g. `1.2.3`) stays at the version, that has been published.
    - `appVersion` increased to the next version as this is used for `DEV` builds.

## Fastlane

When running locally, you must set `TAMMY_BUILD_FLAVOR` to `PROD` (e.g. by prepending `TAMMY_BUILD_FLAVOR=PROD` to each
command).

Install fastlane by installing ruby and running:

```bash
bundle update
```

### Create screenshots

Create new screenshots for the App.

When using a Mac, you may do first (this needs Android SDK command line tools to be installed):

```bash
# macOS
export PATH="$PATH:$HOME/Library/Android/sdk/emulator:$HOME/Library/Android/sdk/tools:$HOME/Library/Android/sdk/cmdline-tools/latest/bin/"

# linux
export PATH="$PATH:$HOME/Android/Sdk/emulator:$HOME/Library/Android/sdk/tools:$HOME/Android/Sdk/cmdline-tools/latest/bin/"
```

After that you can start the emulators:

```bash
./fastlane/run_screenshot_emulators.sh
```

And create screenshots:

```bash
TAMMY_BUILD_FLAVOR=PROD bundle exec fastlane android screenshots
```

After that you can stop the first script and delete the emulators:

```bash
./fastlane/delete_screenshot_emulators.sh
```

## Important environment variables

This does not include default GitLab environment variables that are used.

- AZURE_ARTIFACT_SIGNING_METADATA_FILE_BASE64
- AZURE_TENANT_ID
- AZURE_CLIENT_ID
- AZURE_CLIENT_SECRET
- ANDROID_RELEASE_STORE_FILE_BASE64
- ANDROID_RELEASE_STORE_PASSWORD
- ANDROID_RELEASE_KEY_ALIAS
- ANDROID_RELEASE_KEY_PASSWORD
- APPLE_KEYCHAIN_FILE_BASE64
- APPLE_KEYCHAIN_PASSWORD
- APPLE_TEAM_ID
- APPLE_ID
- APPLE_NOTARIZATION_PASSWORD
- SSH_PASSWORD_APP: Web app deployment
- SSH_PASSWORD_WEBSITE: Website deployment
