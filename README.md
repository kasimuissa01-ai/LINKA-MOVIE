# MovieRoom — Production Android Streaming App

MovieRoom is a high-performance, full-featured streaming application built with **Kotlin**, **Jetpack Compose (Material 3)**, **Media3 ExoPlayer**, **Room**, and **Cloudflare R2** object storage.

---

## 1. Features Overview

### 🎬 User Experience & Navigation
- **Cinematic Dark Theme**: Custom dark palette (`ObsidianBlack`, `CinematicRed`, `AmberGold`, `ElectricBlue`) engineered for immersive viewing.
- **Home Screen**:
  - Auto-rotating **Hero Carousel** with large cover art, metadata badges, and direct Play/Download CTAs.
  - Horizontally scrolling categorized rows (*Popular on MovieRoom*, *Sci-Fi & Cyberpunk*, *Action & Thrillers*, *Cinematic Epics*).
- **Explore & Search**:
  - Live filter text field with instant query debouncing.
  - Multi-tag genre chips (*Sci-Fi*, *Action*, *Adventure*, *Drama*, *Cyberpunk*, *Nature*, *Racing*).
  - High-performance 2-column poster grid with quality indicators (4K HDR).
- **Movie Detail**:
  - Atmospheric gradient scrim, cast badges, genre pills, technical specs, and synopsis.
  - Unified Play/Download buttons with live progress indicator.
- **Offline Downloads Manager**:
  - Streams directly from presigned R2 URLs to app-scoped storage (`getExternalFilesDir`).
  - Persisted in local **Room Database** (`DownloadEntity`).
  - Offline-first playback: Media player detects local file existence and bypasses network.
  - Full control over pause, resume, cancel, and storage cleanup.

### 🎥 Custom Media3 Video Player
- Fullscreen gesture overlay on top of Media3 `PlayerView`:
  - **Single Tap**: Smooth fade toggle of all HUD controls.
  - **Double Tap Seek**:
    - Right half: Forward (+10s) with glowing animated icon and pulse ripple.
    - Left half: Rewind (-10s) with glowing animated rewind icon.
  - **Vertical Swipe Gestures**:
    - Right half vertical swipe: On-screen **Volume HUD** vertical slider with audio level indicator.
    - Left half vertical swipe: On-screen **Brightness HUD** slider modifying window brightness.
  - **Aspect Ratio Cycling**: Cycles through `FIT`, `FILL`, `ZOOM` with on-screen confirmation toast.
  - **Subtitle Selection**: Dialog with subtitle and audio track options.
  - **Playback Speed Selector**: 0.5x, 0.75x, 1.0x, 1.25x, 1.5x, 2.0x.
  - **Lock Screen Mode**: Locks all gestures and controls during viewing to prevent accidental touches.

### 🛡️ Admin Studio & Cloudflare R2 Upload Flow
- **Role Gating**:
  - Built-in simulation switcher between `User` and `Admin` roles in the Profile tab.
  - Protected Admin Studio route inaccessible to standard users.
- **Catalog Management**:
  - Searchable and sortable movie list with direct Edit and Delete actions.
  - Permanent delete dialog that clears catalog and purges R2 objects.
- **Multipart Upload Pipeline**:
  - Chunked 10MB–50MB part uploads coordinated with presigned PUT URLs.
  - Live chunk progress tracking (`Uploading chunk 3/12 to R2...`).
  - Pause, resume, and cancel capabilities backed by Room (`UploadStateEntity`).
  - Assembles parts using Cloudflare R2 / S3 `CompleteMultipartUpload`.

---

## 2. Backend Architecture: Firebase Auth + Firestore + Supabase Edge Functions + Cloudflare R2

- **Identity & Metadata**: Firebase Auth (email/password + Google Sign-In) + Cloud Firestore.
- **Secrets & Signed URLs**: Supabase Edge Functions (Deno / TypeScript + `aws4fetch`) — no Cloudflare Workers.
- **Binary Storage**: Cloudflare R2 bucket `stories` (video files + cover images only — never metadata).

### 1. Firebase Setup (Client + Server)
- **Client-Side**: `app/google-services.json` configures Firebase Auth and Firestore with project `movieroom-stream`.
- **Server-Side Secret**: Firebase service account key stored as a Supabase secret (`FIREBASE_SERVICE_ACCOUNT_KEY`).
- **Firestore Collections**:
  - `users/{uid}`: role (`admin` | `user`), `email`, `displayName`
  - `movies/{movieId}`: `title`, `description`, `genre`, `coverUrl`, `r2ObjectKey`, `durationSec`, `sizeBytes`, `uploadedAt`
  - `downloads/{uid}/{movieId}`: `status`, `localPath`, `progress`

### 2. Supabase Edge Functions (Deno/TypeScript)
Located in `/supabase/functions/`:
- **`get-download-url`**: Verifies Firebase ID token, signs short-lived presigned GET URL for requested R2 object in `stories` bucket via `aws4fetch`.
- **`get-upload-url`**: Verifies Firebase ID token, checks `users/{uid}.role == 'admin'` in Firestore via REST API, signs presigned PUT URL.
- **`create-multipart-upload`**: Admin-only, initiates S3 multipart upload on bucket `stories`.
- **`get-part-url`**: Admin-only, signs chunk presigned PUT URL.
- **`complete-multipart-upload`**: Admin-only, completes multipart assembly.

### 3. Deploy Edge Functions via Supabase CLI
```bash
# Configure Secrets
supabase secrets set FIREBASE_PROJECT_ID="movieroom-stream"
supabase secrets set FIREBASE_SERVICE_ACCOUNT_KEY='{"type":"service_account",...}'
supabase secrets set R2_ACCOUNT_ID="your_cloudflare_account_id"
supabase secrets set R2_ACCESS_KEY_ID="your_access_key_id"
supabase secrets set R2_SECRET_ACCESS_KEY="your_secret_key"
supabase secrets set R2_BUCKET_NAME="stories"

# Deploy Functions
supabase functions deploy get-download-url --no-verify-jwt
supabase functions deploy get-upload-url --no-verify-jwt
supabase functions deploy create-multipart-upload --no-verify-jwt
supabase functions deploy get-part-url --no-verify-jwt
supabase functions deploy complete-multipart-upload --no-verify-jwt
```

---

## 4. Environment Variables (`.env` / `BuildConfig`)

Create a `.env` file in the root directory (or use AI Studio Secrets):

```properties
# Cloudflare Worker or API Gateway
MOVIEROOM_API_URL=https://api.movieroom.stream

# Cloudflare R2 Bucket configuration
R2_BUCKET_NAME=movieroom-media
R2_ACCOUNT_ID=your_cloudflare_account_id

# Optional Firebase configuration
FIREBASE_PROJECT_ID=movieroom-stream
```

---

## 5. Building & Running in Android Studio

1. Open Android Studio (Ladybug or newer recommended).
2. Select **File → Open...** and choose the `MovieRoom` project directory.
3. Allow Gradle to sync dependencies automatically.
4. Run the app on an Android device or emulator running API 26+ (Android 8.0 or newer).

### Run Verification Commands:
```bash
# Verify unit tests
gradle :app:testDebugUnitTest

# Assemble Debug APK
gradle :app:assembleDebug
```
