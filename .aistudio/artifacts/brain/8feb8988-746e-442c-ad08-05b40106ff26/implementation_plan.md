# Implementation Plan: TV Series Season Uploads & MovieBox Episode Selector

Fix the TV Series and Season episode uploading pipeline in the Admin Panel and overhaul the Movie Detail Screen episode selector to match the MovieBox reference design, featuring horizontal season pills (`S01`, `S02`...), a 2-column episode card grid, inline player playback, and one-tap episode downloads.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> The following decisions were confirmed based on your choices:
> - **Episode Grid Layout**: 2-column card grid displaying season/episode label (`S01 EP01`), file size, duration, episode title, and a dedicated download action icon.
> - **Card Tap Behavior**: Tapping anywhere on the episode card (except the download icon) immediately loads and plays that episode in the video player above.
> - **Download Tap Behavior**: Tapping the download icon triggers immediate background download for that episode.
> - **Admin Series Mode**: Explicit manual toggle between "Single Movie" and "TV Series" in the Admin Panel, guaranteeing all seasons, episodes, and video sources persist to Firestore, Supabase, and Room without disappearing or flattening into a single movie.

---

## 1. Overview & Core Concept

- **What It Does**:
  1. **Admin Panel Upload Fix**: Resolves the issue where uploading multi-episode series resulted in a single title without seasons or episodes. The admin panel now reliably manages seasons (`S01`, `S02`, etc.), links uploaded video files or stream URLs to specific episodes, and persists complete episode metadata across both local Room DB and remote backends (Firestore/Supabase).
  2. **MovieBox-Inspired Season & Episode Selector**: Redesigns the episode section on the Movie Detail page to match the reference UI:
     - Horizontal season selector tabs (`[S01] [S02] [S03]...`) with clean active pill highlights.
     - 2-column grid of episode cards with `S01 EP01` headers, duration, file size, episode title, and direct download buttons.
     - Tapping any episode card plays it directly in the player at the top of the detail screen.
     - Floating / bottom quick "Download" action button.

- **Target Audience / Persona**: Users streaming and downloading multi-season TV shows, and admins uploading episodic content to the catalog.

---

## 2. User Experience & Visual Design

### Visual Identity & Theme
- **Color Palette**: Dark obsidian background (`#0A0A0C`), elevated card surfaces (`#16161A` / `#202026`), active cinematic red accents (`#E50914`), and clean download action highlights (`#00E676` / `#4CAF50`).
- **Typography & Rhythm**: Bold labels for season/episode codes (`14sp`, Bold), secondary metadata for duration and file size (`11sp`, `TextSecondary`), and crisp titles (`12sp`, SemiBold).

### Movie Detail Screen Layout
```
┌────────────────────────────────────────────────────────┐
│ [ < ]                   Video Player                   │
│                       (Plays Episode)                  │
├────────────────────────────────────────────────────────┤
│ Title: Merlin                                          │
│ ★ 7.9 / 2009-06-21 • Adventure, Drama, Fantasy         │
│ Subtitles: English, Spanish, French, Swahili           │
├────────────────────────────────────────────────────────┤
│ Episodes & Seasons                                     │
│ [ S01 ]   [ S02 ]   [ S03 ]   [ S04 ]   [ S05 ]        │
├──────────────────────────┬─────────────────────────────┤
│ ┌──────────────────────┐ │ ┌─────────────────────────┐ │
│ │ S01 EP01        [ ↓] │ │ │ S01 EP02           [ ↓] │ │
│ │ 350 MB • 44:12       │ │ │ 355 MB • 44:14          │ │
│ │ Episode 1: The Call  │ │ │ Episode 2: Valiant      │ │
│ └──────────────────────┘ │ └─────────────────────────┘ │
│ ┌──────────────────────┐ │ ┌─────────────────────────┐ │
│ │ S01 EP03        [ ↓] │ │ │ All Episodes          > │ │
│ │ 340 MB • 43:52       │ │ │ 13 episodes in S01      │ │
│ │ Episode 3: The Mark  │ │ │                         │ │
│ └──────────────────────┘ │ └─────────────────────────┘ │
├──────────────────────────┴─────────────────────────────┤
│               [   ↓ Download Season   ]                │
└────────────────────────────────────────────────────────┘
```

---

## 3. Key Product Decisions & Trade-Offs

- **Admin Series vs. Movie Mode**:
  - *Chosen Approach*: Clear segmented toggle at the top of the Admin form ("Single Movie" vs "TV Series / Seasons"). When "TV Series" is selected, the form exposes the multi-season episode manager, automatically attaches Episode 1 to the main video if needed, and strictly requires episode lists to be saved into `movie.episodes`.
  - *Rationale*: Eliminates confusion where series uploads were inadvertently saved with an empty `episodes` array and treated as standalone movies.

- **2-Column Episode Card Grid**:
  - *Chosen Approach*: Arrange episodes for the selected season in a 2-column grid instead of a single vertical dropdown or small box chips.
  - *Rationale*: Exactly matches the user's uploaded MovieBox reference screenshot, offering large touch targets, direct download icons, and clear visual hierarchy.

- **Zero Interruption In-Player Playback**:
  - *Chosen Approach*: Tapping an episode card updates the active video in the player at the top of the detail screen without forcing navigation away to an external screen.
  - *Rationale*: Matches the seamless browsing experience from the reference screenshot.

---

## 4. Technical Architecture & Data Strategy

### Component Interaction Architecture
```
┌─────────────────────────────────────────────────────────────────┐
│                    AdminAddEditMovieScreen                      │
│  - Mode: isSeries Toggle (Movie vs TV Series)                   │
│  - Season Tabs & Episode Manager (Add/Edit S01E01, S01E02...)    │
│  - Correctly bundles List<Episode> into Movie model             │
└───────────────────────────────┬─────────────────────────────────┘
                                │ Saves via AdminViewModel
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                  MovieRepository & Data Layer                   │
│  - Serializes episodes to Room DB (episodesJson)                │
│  - Saves List<Map> and JSON to Firestore & Supabase             │
│  - Preserves episode lists during remote sync                   │
└───────────────────────────────┬─────────────────────────────────┘
                                │ Emits Movie with List<Episode>
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                       MovieDetailScreen                         │
│  - Video Player Header (plays selected episode)                 │
│  - Season Selector Bar: [S01] [S02] [S03]...                    │
│  - 2-Column Episode Grid: S01EP01, size/duration, download icon │
│  - Download Manager integration (EpisodeDownloadWorker)         │
└─────────────────────────────────────────────────────────────────┘
```

### Affected Files
1. **`app/src/main/java/com/example/presentation/screens/AdminAddEditMovieScreen.kt`**:
   - Ensure the `isSeries` mode toggle reliably locks into Series mode whenever episodes are present or when toggled.
   - Fix save actions (`addMovieWithMultipartUpload`, `updateMovie`, `publishMovieDirectly`) to guarantee `finalEpisodesList` is never dropped or emptied.
   - Synchronize Season 1 Episode 1 video upload cleanly into `episodesList`.

2. **`app/src/main/java/com/example/presentation/viewmodel/AdminViewModel.kt`**:
   - Verify `updateMovie` and `addMovieWithMultipartUpload` preserve episode objects, remote R2 keys, and video stream URLs when saving.

3. **`app/src/main/java/com/example/presentation/screens/MovieDetailScreen.kt`**:
   - Overhaul `MovieEpisodesSection` to implement the MovieBox-inspired design:
     - Horizontal season selector tabs (`S01`, `S02`, `S03`...) with active pill styling.
     - 2-column card grid with `Sxx EPxx`, duration, file size, title, and download icon.
     - Tapping the card switches the active episode in the player above.
     - Tapping the download icon starts downloading that episode offline.

---

## Verification Plan

1. **Build Compilation**: Run `compile_applet` to ensure syntax, parameters, and Compose layouts compile with zero errors.
2. **Admin Series Upload Verification**:
   - Verify that adding a series with multiple episodes preserves all episodes and seasons.
   - Verify that editing an existing series keeps all existing episodes and allows adding new seasons/episodes.
3. **Movie Detail Screen Verification**:
   - Verify that series titles display the horizontal season tabs (`S01`, `S02`...).
   - Verify that switching season tabs updates the 2-column grid with episodes from that season.
   - Verify that tapping an episode card immediately loads and plays it in the video player above.
   - Verify that tapping the download icon on an episode card starts the episode download.
4. **Unit Tests**:
   - Run `gradle :app:testDebugUnitTest` to confirm all existing unit tests pass without regressions.
