# Changelog

## Unreleased

### New
- Smart reading status: a tracker title moves to Reading when you read it, resumes from On hold or Dropped, and becomes Completed when you reach the last chapter. Turn it off in Services.
- Smart reading status also restarts a Completed title as Re-reading when you start it over from chapter 1.
- Tracker dates: Completed sets the finish date, and finishing a title never started also sets its start date (AniList, MyAnimeList, Kitsu, MangaBaka).
- Pause stale titles: Reading titles you have not opened for 30 to 180 days move to On hold on your trackers (off by default).
- Tracker sync report in Services: lists titles whose progress differs between the app and your trackers, with Sync all.
- Indonesian translations for the tracker, auto-track and favourites strings.
- Blur sensitive covers: covers of 18+ titles and the genres you choose are blurred in lists, history, the feed and details, and holding a cover lets you peek.
- Open "Needs review" and tap "Link all to nearest entry" to link the titles that need review to their closest tracker entry.
- Downloads redesign: filter chips, sections, sorting, a menu on every row, move up and down in the queue, and retry all failed.
- Downloads can wait for the charger, retry failed pages with growing delays, and each source can set its own download speed and whether read chapters are deleted.
- Downloads shows how much space your manga use, with the biggest ones to delete in a tap. Drag the handle on a waiting download to reorder the queue. The Downloads screen is rebuilt with swipeable Active, Failed and Completed tabs, a progress ring on each download and a storage card, using the same toolbar, menus, dialogs and cards as the rest of the app. Finished downloads are one history entry per manga, and "Download missing chapters" queues what you don't have yet.
- Pick your own font: add .ttf or .otf files in Settings > Appearance > Font, switch between them anytime, and the app uses the one you pick everywhere.

### Improved
- The app is now called ShotenSauce.
- The Themed and Mono app icons (and every themed colour) now use the ShotenSauce logo.
- Secret space no longer adds manga shortcuts to the launcher, and leftovers from earlier are removed.
- Refreshing a comic also sends your progress to trackers that are behind.
- The tracker sheet shows your score in the tracker's own scale (like 10/10) next to the stars, and AniList score format changes are picked up on refresh.
- Tap the score number in the tracker sheet to type an exact score in the tracker's scale.
- Titles you already favourited from another source get a duplicate icon and that source's logo in the top-left pill of the cover.
- Frosted glass is removed, along with its switch and strength slider in Appearance.
- "Download new chapters" moved from Check new chapters to Downloads, and the page, manga and retry limits sit on their own Speed and retries screen.
- Plainer wording across downloads. Notifications show the state (downloading, waiting, paused, failed) and download settings are grouped into Storage and Network and speed.
- Synced with DropSauce 1.1.5

### Fixed
- Restoring a Mihon backup no longer crashes by overwriting settings with the wrong type.
- Tracker messages in Services show in a dialog instead of a snackbar.
- Auto-track now finds titles with quotes, backticks and other symbols by also searching a cleaned-up version of the name.
- "Sync progress from tracking services" no longer shows as an oversized highlighted card in Services.

## 1.1.5 (2026-11-20)

### New
- Pick your app icon: default, mono, or one that matches your theme (settings > appearance)
- Search several genres at once, and add your own genre chips
- Up to 6 sections in the bottom bar
- Show only manga or only novels in Explore (just hold the tab)
- Sleep timer for text to speech
- Retry all failed downloads in one go

### Improved
- PDFs open instantly, even huge ones, and don't take up extra space
- Genre search gives much better results, the same way Mihon does it
- Migrate any extension, not just broken ones, and choose where to look
- Alternatives finds a lot more matches
- Better on tablets and in landscape: chapters sit next to the details, and the continue button shows everywhere
- The favorite button is back to one simple tap

### Fixed
- Kotatsu backups restore properly from around 90 sources like MangaDex, Webtoons and Asura
- Deleting one chapter of an imported folder no longer deletes the whole thing
- Folders with both comics and EPUBs now import both
- Search suggestions are back
- Plenty of download, reader and other small fixes

## 1.1.4 (2026-11-15)

This is a small update you can skip: it is mainly there to get the app onto F-Droid, with some small UI changes.

### Improved
- Redesigned the display options menu and the reader options menu for manga and novels
- Two page layout options moved to settings > reader, and now on by default
- Backups now include pinned sources, extension settings, saved filters, novel plugin settings and every manga's reader mode

### Fixed
- Bookmarking a page again after removing its bookmark failing with an error
- Random crash when the app was reopened in the background
- Backups missing source settings and chapters, and restores stopping early on a damaged entry
- Backups no longer carry device-only settings like storage folders or the Discord login

## 1.1.3 (2026-11-13)

### New
- JPEG XL image support (fixes sources that use it, like Kagane)
- Quick add to default category button on the details page (hold it to change or disable)
- Option to split wide pages manually with a button instead of automatically (settings > reader > fit wide pages)
- Extensions per row option (settings > extensions)

### Improved
- Refreshed settings design with visual previews for theme, list layout, and reader mode
- Smooth fade transition when switching between light and dark theme
- Faster, lower-memory loading for AVIF and JPEG XL pages
- Pull to refresh in extensions now fully reloads the list
- Sort order and chapter sort menus are now submenus
- Smoother description expand and collapse animation on the details page
- Outlined popups

### Fixed
- Excluded genres showing up in suggestions
- OLED dark mode backgrounds
- EPUB dictionary popup not expanding or scrolling properly
- Other small bug fixes
