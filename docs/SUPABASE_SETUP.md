# Supabase Edge Functions & Firebase Architecture Setup

This project uses **Supabase Edge Functions (Deno / TypeScript)** instead of Cloudflare Workers to handle all secrets and sign URLs for **Cloudflare R2** (bucket `stories`).

---

## 1. Secrets Configuration (`supabase secrets set`)

These secrets are configured once in your Supabase project and **never shipped in the Android client application**:

```bash
# Firebase Project Configuration
supabase secrets set FIREBASE_PROJECT_ID="movieroom-stream"

# Firebase Service Account (Used to verify tokens & query Firestore REST API)
# Option A: Full JSON string
supabase secrets set FIREBASE_SERVICE_ACCOUNT_KEY='{"type":"service_account","project_id":"movieroom-stream","private_key_id":"...","private_key":"-----BEGIN PRIVATE KEY-----\n...\n-----END PRIVATE KEY-----\n","client_email":"firebase-adminsdk@movieroom-stream.iam.gserviceaccount.com"}'

# Option B: Individual fields
supabase secrets set FIREBASE_CLIENT_EMAIL="firebase-adminsdk@movieroom-stream.iam.gserviceaccount.com"
supabase secrets set FIREBASE_PRIVATE_KEY="-----BEGIN PRIVATE KEY-----\n...\n-----END PRIVATE KEY-----\n"

# Cloudflare R2 Credentials (S3-compatible, scoped to bucket 'stories')
supabase secrets set R2_ACCOUNT_ID="your_cloudflare_account_id"
supabase secrets set R2_ACCESS_KEY_ID="your_r2_access_key_id"
supabase secrets set R2_SECRET_ACCESS_KEY="your_r2_secret_access_key"
supabase secrets set R2_BUCKET_NAME="stories"
```

---

## 2. Deployed Edge Functions

Located in `/supabase/functions/`:

| Function | Method | Auth Requirement | Description |
|---|---|---|---|
| `get-download-url` | `POST` / `GET` | Any authenticated user (Firebase ID Token) | Signs short-lived presigned GET URL for requested R2 object in `stories` bucket via `aws4fetch`. |
| `get-upload-url` | `POST` | Admin only (`users/{uid}.role == 'admin'`) | Validates ID token and checks Firestore role, then signs presigned PUT URL for R2 object. |
| `create-multipart-upload` | `POST` | Admin only | Calls R2 S3 `CreateMultipartUpload` on bucket `stories`, returns `uploadId`. |
| `get-part-url` | `POST` | Admin only | Signs presigned PUT URL for a specific part chunk (`partNumber` + `uploadId`). |
| `complete-multipart-upload` | `POST` | Admin only | Assembles part ETags on Cloudflare R2 bucket `stories` via `CompleteMultipartUpload`. |

### Deploy via Supabase CLI:
```bash
supabase functions deploy get-download-url --no-verify-jwt
supabase functions deploy get-upload-url --no-verify-jwt
supabase functions deploy create-multipart-upload --no-verify-jwt
supabase functions deploy get-part-url --no-verify-jwt
supabase functions deploy complete-multipart-upload --no-verify-jwt
```
*(Note: JWT verification is handled inside the function code directly by validating the Firebase ID token signature against Google's public certs).*

---

## 3. Firestore Document Schema

1. **`users/{uid}`**:
   - `role`: `"admin"` | `"user"`
   - `email`: string
   - `displayName`: string
   - `createdAt`: timestamp

2. **`movies/{movieId}`**:
   - `title`: string
   - `description`: string
   - `genre`: list of strings (e.g. `["Sci-Fi", "Cyberpunk"]`)
   - `coverUrl`: string (public or signed R2 cover URL)
   - `r2ObjectKey`: string (path inside `stories` bucket, e.g. `movies/neon_horizon.mp4`)
   - `durationSec`: integer (e.g. 7680)
   - `sizeBytes`: integer/long (e.g. 1488977920)
   - `uploadedAt`: timestamp
   - `rating`: double (e.g. 4.9)
   - `releaseYear`: integer (e.g. 2026)

3. **`downloads/{uid}/{movieId}`**:
   - `status`: `"QUEUED"` | `"DOWNLOADING"` | `"PAUSED"` | `"COMPLETED"` | `"FAILED"`
   - `localPath`: string (e.g. `/data/user/0/.../movie_m_cyber_01.mp4`)
   - `progress`: float (0.0 to 1.0)
   - `updatedAt`: timestamp
