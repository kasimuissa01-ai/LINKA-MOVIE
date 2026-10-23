# Implementation Plan: Category Deduplication & Screenshot-Inspired Movie Cards

Restructure the home screen catalog display to eliminate movie duplicates across category rows, establish strict category assignment (movies can appear in Trending and exactly one specific genre), and modernize movie cards to match the clean aesthetic of the reference screenshots.

---

## User Decisions & Clarifications
1. **Deduplication Strategy**: Movies can appear in **Trending Movie** and in **one specific genre only** (no movie will ever be repeated across multiple genre rows like Action, Romance, Horror, etc.).
2. **Top Navigation**: Keep the existing hero banner at the top, flowing directly into the category sections without extra top sub-tabs.
3. **Card & Header Aesthetic (from Reference Screenshots)**:
   - Clean portrait movie cards (~115.dp width, ~170.dp height, 2:3 ratio).
   - Rounded corners (10–12.dp) with clean image cropping and smooth press animation.
   - Remove cluttered overlay badges that obstruct the movie poster art.
   - Movie title positioned cleanly beneath the poster card (1–2 lines with ellipsis).
   - Category headers featuring the genre title on the left (e.g., "Trending Movie", "💖 Romance 💖", "Action", "Horror", "Comedies", "Animation") and a sleek "More >" action link on the right.

---

## Proposed Changes

### 1. Movie Deduplication & Category Grouping Logic
#### `app/src/main/java/com/example/presentation/viewmodel/MovieViewModel.kt`
- Create a dedicated deduplication helper:
  - **Trending Row**: Top rated / featured movies (up to 8 items).
  - **Genre Rows**: Assign each movie strictly to its **primary genre** (the first matching genre in its tags: Romance, Action, Horror, Comedy, Animation, Sci-Fi, Drama).
  - Track assigned movie IDs across genre sections so that if a movie has multiple tags (e.g., `["Action", "Sci-Fi", "Drama"]`), it is only added to the first matching category and excluded from all other genre rows.
  - Expose categorized lists (`trendingMovies`, `romanceMovies`, `actionMovies`, `horrorMovies`, `comedyMovies`, `animationMovies`, `otherMovies`) with zero repetition among the genre lists.

---

### 2. Modern Movie Poster Card Overhaul
#### `app/src/main/java/com/example/presentation/components/MovieCards.kt`
- Refactor `MoviePosterCard`:
  - Set card width to `115.dp` and height to `172.dp` (true 2:3 cinematic poster aspect ratio).
  - Clip to `RoundedCornerShape(10.dp)` with subtle elevation and background placeholder.
  - Remove intrusive badges obscuring the front artwork (like the big top tags) to match the clean, unblocked poster presentation in the screenshot.
  - Place the movie title directly beneath the poster image in crisp `TextPrimary` typography (13.sp, medium/semibold, maxLines = 1 or 2 with `TextOverflow.Ellipsis`).
  - Keep touch ripple / scale animation for a smooth, tactile feel on click.

---

### 3. Home Screen Category Rows & Headers
#### `app/src/main/java/com/example/presentation/screens/HomeScreen.kt`
- Update `MovieRowSection`:
  - Replace the old "See All" text with the screenshot-styled `"More >"` or `"More ❯"` indicator in `TextSecondary` / subtle tint.
  - Support category icons or emoji flair where appropriate (e.g., `"💖 Romance 💖"`, `"Trending Movie"`, `"Action"`, `"Horror"`, `"Comedies"`, `"Animation"`).
  - Horizontal `LazyRow` spacing tuned to `10.dp` for a dense, visually engaging shelf layout.
  - Clicking `"More >"` navigates to the filtered catalog view for that specific category.
- Remove duplicate rows:
  - Eliminate the generic "All Movies" row right above the genre rows which caused immediate visual repetition.
  - Sequence rows cleanly: Hero Banner -> Trending Movie -> 💖 Romance 💖 -> Action -> Horror -> Comedies -> Animation.

---

## Verification Plan

1. **Compilation**: Run `compile_applet` to verify all Compose components build without error.
2. **Deduplication Verification**:
   - Check that no movie appears in more than one genre category shelf.
   - Verify that Trending displays top movies while each genre row presents distinct titles.
3. **Card Presentation**:
   - Inspect poster aspect ratio (115dp x 172dp), title layout underneath, and clean poster art without obscuring badges.
4. **Unit Tests**:
   - Run `gradle :app:testDebugUnitTest` to verify repository and viewmodel logic integrity.
