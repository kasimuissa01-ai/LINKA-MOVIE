# Structured Series & Season Movie Upload & Playback Flow

Streamlined content architecture for uploading and organizing multi-episode seasons and series movies, featuring a dedicated Content Type toggle (Single Movie vs. TV Series / Season), automatic Episode 1 mapping from media pickers, rapid auto-incrementing episode uploads, and guaranteed Season 1 Episode 1 playback priority.

## User Review & Critical Decisions

> [!IMPORTANT]
> Based on your confirmed choices, here is the approved design and architecture:
>
> - **Confirmed Decision 1 (Content Type Toggle)**: The upload screen will feature a clear segmented toggle between **Single Movie** and **TV Series / Season**. When TV Series is active, the upload UI transitions into a season-aware series structure with Episode 1 prominently integrated.
> - **Confirmed Decision 2 (Quick Add Auto-Increment)**: Adding Episode 2 and subsequent episodes will feature a dedicated **"+ Quick Add Episode"** action that automatically detects the highest episode number in the current season and increments it (e.g. S1E2, S1E3, S1E4) with gallery/file picker shortcuts and auto-filled titles.
> - **Confirmed Decision 3 (Guaranteed S1E1 Playback Priority)**: When launching playback from anywhere (Home banner, Movie Details, or fullscreen player), TV series will strictly prioritize and start at **Season 1 Episode 1** (or the lowest available episode) rather than jumping to Episode 2 or an unmapped video stream.
> - **Root Cause Resolution**: The issue where uploading Episode 1 caused the player to show Episode 2 occurred because Episode 1 was saved as the root movie video while the episodes list only contained Episode 2. With the new structure, when TV Series is selected, the first video selected is explicitly registered as **Season 1 Episode 1** in `episodes`, ensuring no video is orphaned or mismatched.

---

## 1. Overview & Core Concept

### What It Does
Provides a unified, error-proof upload experience for admins managing movies and TV series. Admins can seamlessly switch between single standalone films and multi-episode TV series. For series, admins can upload Season 1 Episode 1 directly from their gallery or file manager, and rapidly add Episode 2, Episode 3, and onwards across seasons with minimal taps and instant auto-sequencing.

### Target Audience & Persona
Content administrators managing the video catalog who need an intuitive, fast mobile-friendly workflow to upload and organize episodic content directly from their device storage or gallery.

### Key Value
- **Zero Confusion**: Eliminates the ambiguity between root "Movie Video" and episodic "Series Episodes".
- **Rapid Uploads**: Quick-add buttons with automatic numbering (e.g. Season 1, Episode 2, 3, 4...) and single-tap file selection.
- **Accurate Playback**: Guarantees that viewers always start at Season 1 Episode 1 upon opening a series, with clean season tabs and episode badges.

---

## 2. User Experience & Visual Design

### Key User Flows

#### A. Selecting Content Type
1. Admin opens **Admin Studio -> Upload Movie**.
2. At the very top of the media upload section, a prominent M3 Segmented Button / Pill Selector displays:
   - 🎬 **Single Movie** (Standard full-length film upload)
   - 📺 **TV Series / Season** (Multi-episode season management)
3. Switching to **TV Series / Season** automatically configures the season structure:
   - If no episodes exist yet, a primary card for **Season 1, Episode 1 (Pilot)** appears with direct **Gallery** and **Files** buttons.
   - Any video selected is explicitly bound to `episodes[0]` as `Season 1, Episode 1`.

#### B. Adding Episode 2 and Subsequent Episodes
1. Directly below Episode 1, a prominent **"+ Add Episode 2"** (or next available number) button is displayed with an auto-increment counter.
2. Tapping it opens the quick episode card or modal pre-filled with:
   - Season: Current active season tab (default: Season 1).
   - Episode: Next consecutive number (`max(episodeNumber) + 1`).
   - Title: Pre-filled with `"Episode X"` or custom name.
   - Video Source: One-tap access to **Gallery (Photo Picker)** or **Files (Storage Document Picker)**.
3. Once picked, the episode card displays the file name, size in MB, and a green "Ready to Upload" pill.

#### C. Multi-Season Navigation
1. Admins can tap `+ New Season` to create Season 2, Season 3, etc.
2. Season tabs let the admin effortlessly switch views and add episodes specific to that season.

#### D. Detail Screen & Playback Experience
1. In `MovieDetailScreen`, series show clean season pills (`Season 1`, `Season 2`) and a vertical list of episodes.
2. The primary "Play" hero button and inline player automatically load **Season 1 Episode 1**.
3. Viewers can tap any individual episode card to switch active playback immediately.

### Visual Identity & Theme
- **Theme**: Premium Cinematic Dark theme (`ObsidianBlack` #0D0F14, `SurfaceDark` #161922, `SurfaceElevated` #222634).
- **Accents**: `CinematicRed` (#E50914) for primary creation actions, `ElectricBlue` (#2979FF) for episode badges, and `EmeraldGreen` (#00C853) for completed file selection indicators.
- **Touch Targets**: All episode buttons, file picker triggers, and season tabs adhere to >= 48dp touch targets.

---

## 3. Key Product Decisions & Trade-Offs

### Decision 1: Explicit Content Type Selector vs. Implicit Detection
- **Chosen Approach**: Explicit toggle at the top of the upload form between `Single Movie` and `TV Series / Season`.
- **Why**: Eliminates user confusion. Admins immediately know whether they are creating a movie with a single video or an episodic series.
- **Alternative Considered**: Detecting series solely by whether episodes are added in a sub-dialog. Rejected because users inadvertently uploaded Episode 1 to the movie root and Episode 2 to the sub-dialog.

### Decision 2: Automatic S1E1 Sync & Backward Compatibility
- **Chosen Approach**: When `TV Series` is active, Episode 1's video URL/key is automatically synchronized with both the `episodes` list and the root `videoStreamUrl`/`videoKey` fallback.
- **Why**: Ensures backward compatibility with any player screen or catalog query while guaranteeing that `movie.episodes` contains an explicit `S1E1` record.

### Decision 3: Quick Add vs Multi-Step Dialog
- **Chosen Approach**: In-line quick add cards with pre-calculated episode numbers (`S${season}E${nextNumber}`) and direct file pickers.
- **Why**: Dramatically speeds up uploading multiple episodes sequentially without having to re-enter season numbers or episode IDs manually.

---

## 4. Technical Architecture & Data Strategy *(Technical Reference)*

### Architecture & Component Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                 AdminAddEditMovieScreen                     │
│                                                             │
│  ┌───────────────────────────────────────────────────────┐  │
│  │  Content Type: [ 🎬 Single Movie | 📺 TV Series ]     │  │
│  └───────────────────────────────────────────────────────┘  │
│                                                             │
│  If Single Movie:                                           │
│  ┌───────────────────────────────────────────────────────┐  │
│  │ Single Movie Video Picker (Gallery / File Browser)    │  │
│  └───────────────────────────────────────────────────────┘  │
│                                                             │
│  If TV Series / Season:                                     │
│  ┌───────────────────────────────────────────────────────┐  │
│  │ Season Tabs: [ Season 1 (Active) ] [ + Season ]       │  │
│  ├───────────────────────────────────────────────────────┤  │
│  │ Episode 1 (Pilot) - [ Gallery ] [ Files ] - Selected  │  │
│  │ Episode 2         - [ Gallery ] [ Files ] - Selected  │  │
│  │ [+ Quick Add Episode 3]                               │  │
│  └───────────────────────────────────────────────────────┘  │
│                               │                             │
│                               ▼                             │
│                  adminViewModel.updateMovie()               │
│                  adminViewModel.addMovieWithMultipartUpload│
└───────────────────────────────┬─────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────┐
│               Data Model: Movie & Episode                   │
│                                                             │
│  Movie.kt:                                                  │
│  - isSeries: Boolean = episodes.isNotEmpty()                │
│  - videoStreamUrl: String (S1E1 or single movie)            │
│  - episodes: List<Episode>                                  │
│    ├── Episode(s=1, e=1, title="Episode 1", ...)            │
│    ├── Episode(s=1, e=2, title="Episode 2", ...)            │
│    └── Episode(s=1, e=3, title="Episode 3", ...)            │
└───────────────────────────────┬─────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────┐
│             MovieDetailScreen & PlayerViewModel             │
│                                                             │
│  - Default activeEpisodeId = sortedEpisodes.firstOrNull()   │
│    (Strictly Season 1 Episode 1)                            │
│  - MediaUri resolution: S1E1 plays first                    │
│  - Episode selector row allows one-tap switching to E2, E3  │
└─────────────────────────────────────────────────────────────┘
```

### Data Model & State Mapping
1. **`isSeriesMode` State**:
   - `var isSeriesMode by remember { mutableStateOf(existingMovie?.episodes?.isNotEmpty() == true) }`
2. **Auto-Increment Logic**:
   - For season `s`, `val nextEpNumber = (episodesList.filter { it.seasonNumber == s }.maxOfOrNull { it.episodeNumber } ?: 0) + 1`
   - Quick Add generates `Episode(id = "${movieId}_s${s}_e${nextEpNumber}_${UUID.randomUUID().toString().take(4)}", seasonNumber = s, episodeNumber = nextEpNumber, title = "Episode $nextEpNumber")`
3. **Synchronized S1E1 Storage**:
   - When uploading a series, `episodesList` contains all episodes (1, 2, 3...). The first episode's video key and URL are mirrored into the root `videoKey`/`videoStreamUrl` so existing catalog widgets and home carousels function smoothly.
4. **Player Default Alignment**:
   - In `MovieDetailScreen.kt` and `VideoPlayerScreen.kt`, `sortedEpisodes` sorts by `seasonNumber ASC, episodeNumber ASC`.
   - The initial playback target is strictly `sortedEpisodes.firstOrNull()` which guarantees Season 1 Episode 1 is played.

---

## 5. Verification Plan

### Compilation & Build
- Verify the Android project compiles cleanly via `compile_applet`.

### Functional Tests
1. **Content Type Toggle**:
   - Switch between "Single Movie" and "TV Series / Season" on `AdminAddEditMovieScreen`.
   - Verify the UI adapts cleanly: Single Movie shows the movie video picker; TV Series shows the Season/Episode structure with Episode 1 ready for video selection.
2. **Episode 1 Creation**:
   - Select a video for Episode 1 via Gallery or File Browser.
   - Verify it appears under Season 1 as Episode 1 with proper filename and size.
3. **Quick Add Episode 2 & Consecutive Episodes**:
   - Tap "+ Quick Add Episode".
   - Verify Episode 2 is created with `seasonNumber = 1, episodeNumber = 2`.
   - Select video for Episode 2. Verify both Episode 1 and Episode 2 are preserved and distinct in `episodesList`.
4. **Playback Verification**:
   - Open a TV series in `MovieDetailScreen`.
   - Verify Season 1 Episode 1 is loaded and displayed as active, and Episode 2 can be tapped to switch playback without confusion.
