# Implementation Plan - Fix Admin Dashboard Layout & Catalog Visibility

Fix the layout collapse bug in `AdminDashboardScreen.kt` that caused the action buttons to sit in the vertical middle of the screen with a blank black page, restoring full visibility of the "Admin Studio" header, total movies count, R2 storage metrics, search bar, and detailed movie cards with edit/delete functions.

## User Review Required
> [!IMPORTANT]
> - **Header & Action Bar Separation**: The top header ("Admin Studio" title + back button) and the scrollable action bar (`+ Admins`, `Analytics`, `Refresh`, `Sync R2 URLs`) will be cleanly separated into two distinct rows pinned at the top, eliminating the horizontal constraint conflict that squashed the header.
> - **Unified Single-Root Scrolling**: The screen will use a single root `LazyColumn` inside a standard Material 3 `Scaffold`, completely eliminating the nested `Column(fillMaxSize) + LazyColumn(fillMaxSize)` bug that caused the content to collapse into the vertical center of the screen.
> - **Catalog Visibility**: All uploaded movies, the total movies count, storage metrics, trending switches, episode managers, edit buttons, and delete buttons will be fully visible and scrollable.

---

## Proposed Changes

### Presentation / Admin UI
#### `app/src/main/java/com/example/presentation/screens/AdminDashboardScreen.kt`
- **Scaffold Architecture**:
  - Use `Scaffold` with `containerColor = ObsidianBlack` and `statusBarsPadding()`.
  - **Top Bar**:
    - Row 1: Back icon button (`IconButton`), `Admin Studio` title (22.sp, bold), and `Cloudflare R2 & Supabase Backend` subtitle.
    - Row 2: Scrollable `LazyRow` with action buttons:
      - `+ Admins` (green button, for main admins `255696102700` and `255716123283`)
      - `Analytics` (with AmberGold icon)
      - `Refresh` (with spinner when syncing)
      - `Sync R2 URLs` (CinematicRed)
  - **FAB**:
    - Floating Action Button with `+ Add Movie` pinned at bottom-right (`Modifier.testTag("admin_add_movie_fab")`).
  - **Content (`LazyColumn`)**:
    - **Item 1: Sync Status Banner** (green alert card if a sync or repair message is active).
    - **Item 2: Metrics Row**:
      - `TOTAL MOVIES` card with live count `${movies.size}`.
      - `R2 STORAGE` card with total formatted storage `${totalSizeMb / 1024.0} GB`.
    - **Item 3: Search Input Field**:
      - Filter text field with search icon (`admin_search_input`).
    - **Item 4: Empty State / Guided Studio Card**:
      - Shown when no movies match the query or catalog is empty, with sync and add movie buttons.
    - **Items: Detailed Admin Movie Cards (`filteredMovies`)**:
      - Cover thumbnail image with fallback.
      - Title, release year, duration, file size.
      - Origin country badge (e.g. USA, Tanzania, India) and Swahili translation badge.
      - One-tap Trending toggle switch (`★ Trending` with AmberGold star).
      - Episodes button showing episode count (`admin_episodes_button_${movie.id}`).
      - Change Cover button (`admin_cover_button_${movie.id}`).
      - Edit button (navigates to edit screen, gated by admin upload ownership).
      - Delete button (with confirmation dialog, gated by admin upload ownership).

---

## Verification Plan
1. **Compilation Verification**:
   - Run `compile_applet` to ensure zero compilation or syntax errors.
2. **Automated Unit Testing**:
   - Run `gradle :app:testDebugUnitTest` via `run_command` to verify all unit and Robolectric tests pass.
3. **Layout & Architecture Verification**:
   - Confirm the header is fixed at the top of the screen (not in the vertical middle).
   - Confirm `TOTAL MOVIES` and `R2 STORAGE` cards render directly beneath the action bar.
   - Confirm movie catalog items render with full edit, delete, trending, and episode controls.
