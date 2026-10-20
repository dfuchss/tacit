# Tacit Changelog

All notable changes to Tacit are documented in this file. Each version notes the Tammy version it is based on;
upstream changes are tracked in [CHANGELOG.md](CHANGELOG.md).

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/). Versions follow
`<upstream version>-tacit.<n>`.

## [Unreleased]

### Added

### Changed

### Fixed

- The unread badge of the topmost guild is no longer cut off at the top
- Quitting or "Restart to update" no longer leaves Tacit running without a window when the sync was busy or retrying; the update now installs
- On Windows and Linux, Tacit's process now always ends after quitting, so it no longer keeps the single-instance port and swallows later launches
- On Windows and Linux, starting Tacit again while it runs now brings its window back from the tray or taskbar, also on the profile screen; opening it from the tray also restores a minimised window

## 26.9.2-tacit.6

Based on Tammy 26.9.2.

### Changed

- Tacit always uses its dark theme, including Tammy's dialogs and context menus
- Sender name colours are tuned to Tacit's dark palette and the message cards they sit on
- The first window opens sized to the usable screen area
- The Tacit logo in the guild rail is sharp on displays at 100% scaling instead of speckled
- Picking a user from a search keeps the other results instead of searching again

### Fixed

- On macOS, notifications and the Dock badge no longer stay after the message was read right away
- Completing a shortcode or slash command replaces the whole word around the cursor, also for non-ASCII shortcodes
- Opening a room no longer moves the focus past the newest message
- A message right after a room event starts a new sender block with name and avatar
- The highlight after jumping to a message fades in instead of flashing
- A dialog shows the same error again when it happens a second time
- Dragging a guild drops the guild that was dragged, even if the list changed meanwhile
- Generated avatar initials are centred at small sizes

## 26.9.2-tacit.5

Based on Tammy 26.9.2.

### Added

- The create-category dialog explains how to move a channel into the new category
- "Mark all as read" also clears leftover notifications of rooms you already left and tells you about pending invites,
  which can keep the unread badge up

### Changed

- Quoted text (`>` blockquotes) uses the same full-width card style as reply quotes
- No more tooltips on room events, date pills and the back/close arrow of headers that only repeated the visible text
- Your own invite and join are grouped with the other room events, with the encrypted-history notice above the group
- The typing indicator lines up with the message cards
- More of the UI is translated to German (typing indicator, slash commands, error messages)
- Avatars load without an in-memory cache, so a failed avatar is retried instead of staying blank
- Builds and releases only run after the tests pass

### Fixed

- Moving a room to another category no longer leaves it in the old one
- Dialogs can no longer be submitted twice, and show their errors inside the dialog
- Starting a direct message only reuses a joined direct chat, not a left room or a space
- Emotes no longer show "null" as the sender

## 26.9.2-tacit.4

Based on Tammy 26.9.2.

### Added

- Setting to switch between the bundled font and the system font (Appearance > Accessibility), as in Tammy
- Runs of room events (joins, invites, room settings, …) collapse into one row such as "6 room events by Alice and
  Bob" that expands on click
- Clicking a reply quote briefly highlights the message it jumps to

### Changed

- Reply quotes are redesigned: a full-width card tinted in the quoted sender's colour, with their avatar and name
- The composer shows a "Replying to …" card while writing a reply
- Room events are shown as muted pills instead of bright accent pills
- Updated README screenshots

### Fixed

- Linux docks and app menus show Tacit's name and icon (upstream fix CMP-3308)
- The profiles settings entry is only shown when profiles are available
- The macOS Dock icon is no longer larger than other apps' icons

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
