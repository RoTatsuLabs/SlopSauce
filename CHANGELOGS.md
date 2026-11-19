# Changelog

All notable changes to SlopSauce are listed here, newest version first. Entries sit under Additions, Changes, Fixes, or Other, and each release gets a heading like `## [1.0.0] - 2026-01-31`.

## [Unreleased]

### Additions

- Hold "Track untracked favourites" to link the titles that need review to their nearest tracker entry.
- Downloads redesign: filter chips, sections, sorting, a menu on every row, move up and down in the queue, and retry all failed.
- Downloads can wait for the charger, retry failed pages with growing delays, and each source can set its own download speed and whether read chapters are deleted.
- Downloads shows how much space your manga use, with the biggest ones to delete in a tap. Drag the handle on a waiting download to reorder the queue. The Downloads screen is rebuilt with swipeable Active, Failed and Completed tabs, a progress ring on each download and a storage card, using the same toolbar, menus, dialogs and cards as the rest of the app. Finished downloads are one history entry per manga, and "Download missing chapters" queues what you don't have yet.
- Pick your own font: add .ttf or .otf files in Settings > Appearance > Font, switch between them anytime, and the app uses the one you pick everywhere.

### Changes

- "Download new chapters" moved from Check new chapters to Downloads, and the page, manga and retry limits sit on their own Speed and retries screen.
- Plainer wording across downloads. Notifications show the state (downloading, waiting, paused, failed) and download settings are grouped into Storage and Network and speed.

### Fixes

- "Sync progress from tracking services" no longer shows as an oversized highlighted card in Services.

## [1.1.4] - 2026-11-17

TL;DR: Frosted glass, the latest DropSauce changes, and better tracker import and auto-track.

### Additions

- Frosted glass: dialogs, sheets, menus, the reader bars and the navigation bar turn see-through with a blurred background. Switch it off in Appearance.

### Changes

- Merged the latest DropSauce changes (up to its 1.1.4).

### Fixes

- Tracker import no longer shows raw Markdown in descriptions, and "Fill if empty" no longer changes a description that is already there.
- Auto-track matches more titles: it compares every name, searches alternative titles and skips entries of another kind.

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
