# Implementation Plan - 10-Movie Row Arrangement & Clean Category Distribution

Organize movie rows on the Home screen and Filtered views so that every row contains exactly up to 10 movie cards, ordered by newest and trending first, with overflow movies continuing in dedicated "More [Category]" rows.

## User Review Required
> [!IMPORTANT]
> - **10 Cards Per Row Max**: Every horizontal shelf will be chunked into at most 10 movie cards.
> - **Continuation Rows**: If a category has more than 10 movies, additional movies will flow into a subsequent row labeled **"More [Category]"** (e.g. *Action* -> *More Action*).
> - **Sorting**: Movies are ordered with newest releases, latest uploads, and trending titles first, while older titles flow into continuation rows.
> - **Scope**: Applies to Home screen genre shelves (Trending, Swahili translated, Action, Romance, Zombies, Horror, Comedies, Sci-Fi, Animation, Drama) and active Filtered views.

---

## Proposed Changes

### 1. Movie Sorting & Chunking Utility (`HomeScreen.kt` / `MovieViewModel.kt`)
- Ensure all category lists in `MovieViewModel` or `HomeScreen` are sorted by:
  - Trending status (`isTrending == true` first)
  - Release year / upload date descending (newest first)
  - Rating / title
- Create a reusable composable/helper in `HomeScreen.kt`:
  ```kotlin
  fun renderChunkedShelves(
      primaryTitle: String,
      movies: List<Movie>,
      onMovieClick: (Movie) -> Unit,
      onCategoryClick: ((String) -> Unit)? = null
  )
  ```
  - Splits `movies` into chunks of 10 items (`movies.chunked(10)`).
  - The first chunk renders with the `primaryTitle` (e.g., "Action").
  - Chunks 2, 3, etc., render with titles such as `"More $categoryName"` (or `"More $categoryName (Part 2)"` if more than 2 chunks exist).

### 2. Home Screen Shelves Layout (`HomeScreen.kt`)
- **Filtered Movies**:
  - When filtering by genre or country, divide `state.searchResults` into 10-card rows:
    - Row 1: `Filtered Movies • [Genre/Country]` (first 10 items)
    - Row 2+: `More Filtered Movies (Part 2)` (next 10 items)
- **Dedicated Shelves**:
  - Apply the 10-card chunking to:
    - *Zilizotafsiriwa Kiswahili (DJ Afro & Ma DJ)* & *More Zilizotafsiriwa*
    - *Trending Movie* & *More Trending*
    - *🧟 Zombies 🧟* & *More Zombies*
    - *💖 Romance 💖* & *More Romance*
    - *Action* & *More Action*
    - *Horror* & *More Horror*
    - *Comedies* & *More Comedies*
    - *Animation* & *More Animation*
    - *Sci-Fi* & *More Sci-Fi*
    - *Drama* & *More Drama*
- **Visual Spacing & Clean Arrangement**:
  - Keep 20.dp spacing between rows.
  - Maintain the "More" button linking to full category browse for user convenience.

---

## Verification Plan
1. **Compilation Verification**:
   - Run `compile_applet` to ensure zero compilation or syntax errors.
2. **Automated Unit Testing**:
   - Run `gradle :app:testDebugUnitTest` via `run_command` to verify all existing tests pass without regressions.
3. **Behavioral Checks**:
   - Verify every shelf contains at most 10 items.
   - Verify categories with >10 items cleanly render continuation rows titled "More [Category]".
   - Verify newest and trending items appear in the first row.
