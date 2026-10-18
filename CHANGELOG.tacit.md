# Tacit Changelog

All notable changes to Tacit are documented in this file. Each version notes the Tammy version it is based on;
upstream changes are tracked in [CHANGELOG.md](CHANGELOG.md).

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/). Versions follow
`<upstream version>-tacit.<n>`.

## [Unreleased]

### Added

### Changed

### Fixed

## 26.9.2-tacit.3

Based on Tammy 26.9.2.

### Added

- Desktop self-update on macOS, Windows and Linux: checks for new releases about once a day (can be turned off under
  **Settings** > **About the app**), verifies the download against `SHA256SUMS`, and installs it with
  **Restart to update** after Tacit has fully exited, restoring the previous version if the swap fails
- Releases publish a `SHA256SUMS` file

### Changed

- The About screen shows the full Tacit version, e.g. `26.9.2-tacit.3`
- Updated README screenshots, including the update notice

## 26.9.2-tacit.2

Based on Tammy 26.9.2.

### Changed

- Install instructions explain how to download updates on macOS without the "Open Anyway" prompt
- Updated README screenshots

### Fixed

- Long multi-line messages no longer push the timeline out of view; the message input grows up to six lines and then
  scrolls

## 26.9.2-tacit.1

First Tacit release, based on Tammy 26.9.2.

### Added

- Desktop builds for macOS, Linux and Windows via GitHub Actions
- Docker image for the web version
- Guilds: Matrix spaces shown as a guild rail, with categories (subspaces), browsing, invites and category invites
- Filtering of rooms, and moving invites to the top of the room list
- Mark rooms as read or unread, plus "mark all as read" in the development settings
- Initial slash commands
- Reactions: show who reacted on hover, a new reaction pill and the o-reaction
- Failed and sending messages are shown and can be retried
- Setting to keep the app running in the background only
- Privacy settings entry and refined DM profile UI
- File name shown in the drag and drop overlay
- Hover and focus affordances in the room list and guild rail
- Room list width is resizable and remembered
- Native Dock badge for unread messages on macOS
- Support for MSC4289

### Changed

- New visual style: unified UI elements, always-visible message bubbles, soft glow for mentions, faint message cards
- Consecutive messages from the same sender are grouped, with the time shown beside short messages
- Deleted messages are hidden, and replies to them show a placeholder
- Round online indicator around the avatar, and no unread count on the avatar
- Tacit offline banner replaces the upstream sync error banner
- Read receipt moves to the last event once caught up, and leftover notifications count as unread
- Emoji autocomplete ranks matches and can be dismissed with Escape
- Updated to Trixnity Messenger 4.10.3 and the new setup wizard

### Fixed

- Timeline scrolling: jump to the end, scroll on send, and several edge cases
- No notifications for state events
- Space children without `via` are ignored
- Members button no longer overlaps the room description
- Cropped window icon on macOS
- Alignment of the notice robot, quick menu and read markers in room pills
- Readability of the "cannot send messages" input area
