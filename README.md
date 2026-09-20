# Deepen

**From the beginning.**

Deepen is an open-source Android TV app for watching a YouTube teaching archive in chronological order — starting with the oldest available message and progressing toward the latest.

The MVP is built around one simple question:

> **What is the next message I should watch?**

Instead of browsing, searching, or manually managing a playlist, Deepen remembers the journey and keeps moving forward one message at a time.

## MVP Goal

The first version of Deepen is focused on the public YouTube teaching archive of **Dr. Paul Gitwaza**.

The journey is fixed:

**Oldest available public video → Latest available public video**

There is no onboarding wizard and no manual starting-point selection.

On first use, Deepen loads the available public video archive, orders it chronologically, and makes the oldest unwatched message the current message.

## MVP Features

- Load public videos from the configured YouTube channel
- Order videos from oldest to newest
- Automatically select the oldest unwatched video
- Play videos inside the Android TV app
- Save playback position locally
- Resume a message from where playback stopped
- Mark a video complete when playback ends or reaches the completion threshold
- Automatically advance to the next chronological message
- Show watched versus total journey progress
- Detect newly published videos and append them to the end of the journey
- Work fully with an Android TV remote
- Keep viewing progress locally on the TV

## TV Controls

| Remote | Action |
| --- | --- |
| OK / Select | Play or pause |
| Left | Rewind 10 seconds |
| Right | Forward 30 seconds |
| Back | Return to the journey screen |

The normal viewing experience should not require a mouse, touchscreen, or keyboard.

## How It Works

```text
Open Deepen
     ↓
Load current journey
     ↓
Play oldest unwatched message
     ↓
Save playback progress
     ↓
Complete message
     ↓
Advance to next message
```

Deepen always treats the earliest incomplete video as the current message.

## MVP Technology

- **Platform:** Android TV
- **Language:** Kotlin
- **UI:** Jetpack Compose / Compose for TV
- **Local persistence:** SQLite
- **Archive metadata:** YouTube Data API v3
- **Playback:** YouTube IFrame Player API
- **Player surface:** Android WebView

Deepen does not download or re-host YouTube videos. Playback continues to be provided by YouTube.

## Local Progress

The MVP does not require a Deepen account or backend.

Journey state is stored locally on the Android TV device, including:

- YouTube video ID
- title
- publication date
- playback position
- observed duration
- completion status

Clearing the app data or uninstalling the app may remove this local progress.

## Archive Synchronization

### Initial Sync

On a fresh installation, Deepen retrieves the available public videos from the configured channel, stores their metadata locally, and orders them chronologically.

The oldest available video becomes the beginning of the journey.

### Later Syncs

Later synchronizations check for newly published videos and append them to the existing journey.

Deleted or private videos are skipped when they are unavailable for playback.

## YouTube API Key

Deepen requires a YouTube Data API v3 key to synchronize the video archive.

API credentials must not be committed to the repository.

For local builds:

```bash
gradle :app:assembleDebug -PYOUTUBE_API_KEY=YOUR_API_KEY
```

For GitHub Actions, use a repository secret named:

```text
YOUTUBE_API_KEY
```

Developers building their own copy should use their own API credentials.

## Not Part of the MVP

The first version intentionally does **not** include:

- user accounts
- cloud synchronization
- mobile or web companion apps
- notes or journaling
- multiple teaching archives
- recommendations
- social features
- comments
- manual playlists
- manual starting-point selection
- importing existing YouTube watch history
- extensive search

The MVP should remain focused on one job:

> **Watch a teaching archive from the beginning and continue until you catch up to the latest message.**

## YouTube Content

Deepen does not own or redistribute the videos displayed through the app.

Videos remain hosted and delivered by YouTube. Rights to individual videos remain with their respective creators and rights holders.

## License

Deepen is open-source software licensed under the **GNU General Public License v3.0 (GPLv3)**.

See [LICENSE](LICENSE) for the full license terms.
