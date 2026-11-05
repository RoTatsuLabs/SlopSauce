# Changelog

All notable changes to SlopSauce are listed here, newest version first. Entries sit under Additions, Changes, Fixes, or Other, and each release gets a heading like `## [1.0.0] - 2026-01-31`.

## [Unreleased]

TL;DR: Downloads work like a download manager and can fetch the next chapters while you read. Trackers can import details and track your favourites on their own. SlopSauce also adds a PIN-locked secret space, Telegram backups, a Discord presence that signs in with OAuth2, and a Mihon page animation in the reader.

### Additions

- Secret space, locked with a PIN or your fingerprint. It has its own library, history, bookmarks and downloads, and nothing from it is sent to trackers. You can set an auto-lock time, lock it at once, change the PIN or the unlock method, see the storage it uses, and copy the main app's settings in or start fresh.
- 1DM-style downloads. Pages of a chapter download in parallel, and the downloads screen shows a segmented bar with one segment per page (waiting, downloading, done or failed). The next chapter's page list is fetched while the current one finishes. The settings have "Pages at once (per source)" and "Manga at once" sliders with a line explaining each.
- Auto download of the next chapters. Past half of a chapter, the next one is queued as a normal download. A slider sets how many chapters ahead, from 1 to 10, and a Wi-Fi only switch is on by default. It is off by default and skips incognito reading.
- Auto-track for favourites. With the switch on, a title you add to a category is searched on every tracker you are signed in to and linked when the match is clear. "Track untracked favourites" does the same for titles already in your favourites, asks before it starts, and shows progress and a result list. Your reading progress is sent with the link. Unsure matches go to a "Needs review" list that opens the usual tracker picker.
- A shared rate limiter for all trackers. It spaces requests, reads the limit headers the tracker sends, backs off after a 429 and remembers the pause. Background work keeps part of the limit free for what you do on screen, and waits longer than ten minutes are handed to WorkManager.
- Tracker import. On the override screen, pull genres, alternative names, authors, status, year, rating and the adult flag from a linked tracker. For each field you pick Replace, Add or Fill empty, and fields the tracker lacks are listed in one line. The override screen has a new Details section for tags, authors, status and rating.
- A badge on titles that are already in your favourites under another source, in grid and detailed lists, and a row on the details page that names the source.
- A Hide favourites switch in Appearance settings and in the list display options (3-dot menu). Favourited titles are filtered out of source lists as you browse, and the next page loads when a whole page is hidden. The local library is unaffected.
- Telegram backups. Periodic backups can be sent to a Telegram chat through the built-in bot; press Start in the bot to link your chat. The section is hidden in builds without a bot token. The chat ID is left out of backups and sync.
- Choose what a backup includes. Create backup asks which sections to include, and periodic and Telegram backups use a Backup contents setting. Favourites always bring their categories.
- Discord Rich Presence through the Discord Social SDK, signed in with OAuth2 instead of a user token. You can set the activity name, add two buttons with placeholders, show or hide elapsed time, and use reading status (Do not disturb, Idle, Online). The chapter total counts main chapters only.
- A Mihon entry under Settings > Reading > Page animation. Taps, volume keys and buttons use Mihon's page timing, and swiping in the pager and double-page readers follows Mihon's page choice and velocity.
- A Disable zoom out switch under Settings > Reader > Zoom. In webtoon mode pages can no longer shrink below full width. Zooming in is unchanged.
- The local library scans nested folders. EPUBs at any depth below a library folder are read, so `Title/Volume 1/Title.epub` shows as one series with a volume per file. Hidden folders, symlinks and folders with a `.notamanga` file are skipped.

### Changes

- The app is called SlopSauce, with the SlopSauce logo as its icon and on the startup screen. The application ID is still `org.haziffe.dropsauce`.
- Merged DropSauce 1.1.2: the animated startup screen, redesigned menus and dialogs, split page indicator in the reader, chapter sort that also sets reading order, safe-only extension suggestions, and a Mark as read/unread submenu.
- The tracker import sheet is compact, with a sticky header and footer, mode buttons and an "Import (n)" button.

### Other

- Builds produce one APK per ABI (arm64-v8a, armeabi-v7a, x86_64, x86) plus a universal one.
