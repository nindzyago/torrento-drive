# Changelog

## 0.4.1

- RuTracker posters on the New and Popular shelves: a poster lookup is no longer cancelled
  when its card scrolls away or the tab changes, and posters found are kept on disk, so they
  are not fetched again on every start.
- Bottom panel: Left/Right on the tabs only switch tabs; Down or OK goes into the cards. After
  coming back from a release, returning to its tab no longer pulls focus down into the shelf.

## 0.4.0

- New and popular on every tracker: the home screen's bottom handle is now three tabs,
  Continue watching · New · Popular. New and Popular show ten releases per tracker section,
  under the tracker's own section names, with posters, quality, seeds and size; a filter picks
  one tracker. Back from a release opened there returns to the same card.
- Rutor: Popular is its top of the last 24 hours, New the newest releases of each category.
- RuTracker (after sign-in): sections are the site's top-level forums; New is sorted by
  registration date, Popular by seeders over the last week.
- Video sections are on by default; any section of each tracker can be switched on or off in
  Settings → Trackers. Shelves refresh on app start at most every three hours and are kept on
  disk, nothing runs while the app is closed.
- A new tracker can offer shelves with two optional members (see CONTRIBUTING.md).

## 0.3.1

- The backdrop no longer jumps when the on-screen keyboard opens or closes for typed search.
- Player: the first OK press only brings up the overlay; pause is the second press. OK, then
  up and left/right seeks without stopping the video.

## 0.3.0

- Settings screen behind a gear button on the home screen: connection, trackers, TorrServer,
  language, about. The language and trackers buttons moved there.
- Connection to trackers: Automatic, Direct and ByeDPI (byedpi running inside the app, no VPN).
  A check shows which trackers open and how; the home screen warns when one doesn't.
- Trackers can be switched off; switched-off trackers take no part in searches.
- TorrServer settings: status with the server version, open / install / reinstall TorrServe and
  the install steps.
- Connection methods are pluggable like trackers and languages (see CONTRIBUTING.md).

## 0.2.2

- New default backdrop: an original sunset scene on the right of the home screen, fading out
  softly toward the voice button. A personal `backdrop.png` still replaces it.
- README screenshots show the new backdrop.

## 0.2.1

- "Install TorrServe" button on the home screen: downloads the latest TorrServe from GitHub
  and installs it; the hint also gives its Downloader code.

## 0.2.0

- First public release.
- Built-in player with next-episode auto-play and resume.
- Home screen: voice and typed search with history, "Continue watching" shelf.
- Trackers: RuTracker (sign-in on the site) and Rutor.
- English and Russian; spoken-query and category words live in language packs.
- Shows how to install or start TorrServe when it isn't running.
- Offers updates from GitHub releases.
