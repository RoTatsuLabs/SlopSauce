# Changelog

All notable changes to SlopSauce are listed here, newest version first. Entries sit under Additions, Changes, Fixes, or Other, and each release gets a heading like `## [1.0.0] - 2026-01-31`.

## [Unreleased]

## [1.1.2] - 2026-11-05

TL;DR: Faster downloads, auto-track and import for trackers, a secret space, Telegram backups and Discord presence through the Discord SDK.

### Additions

- Secret space with a PIN or fingerprint lock and its own library, history and downloads.
- 1DM-style downloads: pages in parallel, a progress bar per page and speed settings.
- Auto download of the next 1 to 10 chapters while you read.
- Auto-track for favourites on every signed-in tracker, with a "Needs review" list.
- Tracker import for genres, alternative names, authors, status, year and rating.
- A badge on titles already in your favourites under another source.
- Hide favourites in source lists.
- Telegram backups, and a choice of what a backup includes.
- Discord Rich Presence through the Discord Social SDK, with custom buttons.
- Mihon page animation for the reader.
- Disable zoom out for webtoons.
- Nested folder scan for local manga.

### Changes

- Renamed to SlopSauce with a new logo.
- Merged DropSauce 1.1.2.

### Other

- One APK per ABI plus a universal one.
- Preview builds are signed with the release key.
