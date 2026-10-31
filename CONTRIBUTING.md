# Contributing

Thanks for helping! The most useful contribution is usually **a new tracker** — it is one
Kotlin file plus one line of registration.

## Adding a tracker

Trackers live in `app/src/main/java/io/github/nindzyago/torrentodrive/trackers/`.

1. **Copy the template.** Duplicate `ExampleProvider.kt` as `YourTrackerProvider.kt` and
   rename the class.
2. **Fill in `id` and `name`.** `id` is a stable lowercase key (it is stored in the watch
   history — don't change it later); `name` is shown in the UI.
3. **Implement `search(query)`.** Fetch the tracker's search page and turn each result into a
   `TorrentItem`:

   | field      | what to put there                                                          |
   |------------|----------------------------------------------------------------------------|
   | `trackerId`, `tracker` | your `id` and `name`                                           |
   | `title`    | the release title as the tracker shows it                                  |
   | `size`     | human-readable size text (`"1.4 GB"`)                                       |
   | `seeds`, `peers` | integers; results are sorted by seeds                                 |
   | `link`     | a magnet link — or any id your `source()` knows how to resolve             |
   | `forum`    | optional: the tracker's section name, it improves category detection       |
   | `page`     | optional: the release page URL, used to find the poster                    |

   Run network calls on `Dispatchers.IO`; the shared `Http.client` (OkHttp) sets a browser
   User-Agent. Jsoup is available for HTML.
4. **`source(item)` (optional).** The default hands `item.link` to TorrServer as a magnet/URL.
   Override it if the tracker gives `.torrent` files only — return `TorrentSource.File(bytes)`.
5. **`poster(item)` (optional).** Return the URL of the first picture in the release
   description; it is cached and shown in "Continue watching".
6. **Register it.** Add one line to `Trackers.all()` in `Trackers.kt`.
7. **Try it on a device:**

   ```bash
   ./gradlew assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk
   adb shell am start -n io.github.nindzyago.torrentodrive/.MainActivity --es query "some title"
   ```

### Trackers that need an account or sit behind Cloudflare

Do not send credentials from the app and do not try to get around bot checks. Instead, run
the tracker's traffic through a WebView session, the way `RutrackerWeb` does:

- sign-in happens on the tracker's own login page, opened full screen with the D-pad cursor
  (`requiresSignIn = true`, `isSignedIn`, `startSignIn()`, `signOut()` on your provider);
- searches load the tracker's pages in that WebView and read results with a small piece of
  JavaScript; downloads use `fetch()` inside the page, so cookies apply as in a browser;
- if Cloudflare shows a check, the WebView is brought to the front for the user.

If you need this for a second tracker, consider generalizing `RutrackerWeb` into a reusable
site session — that refactor is very welcome.

### Search and categories

`SearchQuery` strips filler words from spoken phrases ("find me the movie…") and extracts a
quality filter ("in 4K"). `Category` guesses a category from the forum name and the title.
The words for both come from the language packs (see below).

## Adding a language

A language is two files:

1. **UI strings.** Copy `app/src/main/res/values/strings.xml` (English) to
   `app/src/main/res/values-<lang>/strings.xml` and translate it, including the `plurals`.
2. **Language pack.** Copy `app/src/main/java/io/github/nindzyago/torrentodrive/lang/English.kt`
   to `lang/<Language>.kt`, set `tag` (the same code as in `values-<lang>`), `name` (in the
   language itself) and `flag` (an emoji for the language button), and translate the word lists: the commands a spoken query starts
   with, the "kind" words ("movie", "series"), how people say a video quality, and the keywords
   that mark each category in release titles. Entries are lowercase regex fragments, so a stem
   like `"мульт"` matches every word that starts with it. Register the object in
   `Languages.all` in `lang/LanguageWords.kt`.

The language button on the home screen lists every registered language. "Automatic" picks
the first of the system's preferred languages that the app has, else English.

All language packs are used for search together, whatever the UI language is: a Russian tracker has
Russian titles even when the TV is set to English.

Run `./gradlew testDebugUnitTest` and add a case for your language to
`app/src/test/.../SearchQueryTest.kt`.

## Code style

- Kotlin, Jetpack Compose for TV. Everything must be reachable with a D-pad — test with the
  remote, not only with a mouse.
- Keep the app light: it runs on boxes with 2 GB of RAM.
- Comments and commit messages in English.
- Never commit personal files (`backdrop.png`, keystores, `local.properties`).
