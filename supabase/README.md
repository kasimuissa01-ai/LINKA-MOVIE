# MovieRoom - Supabase & Cloudflare R2 Edge Functions Setup Guide

This folder contains all the production-ready Supabase Edge Functions and SQL Schema for Cloudflare R2 video streaming and multipart uploads.

---

### Step 1: Run SQL Schema in Supabase
1. Open your **Supabase Dashboard**: [https://supabase.com/dashboard/project/vqgnxqabvmmpfoiceass](https://supabase.com/dashboard/project/vqgnxqabvmmpfoiceass)
2. Go to **SQL Editor** -> **New Query**.
3. Copy & paste the contents of `supabase/schema.sql` and click **Run**.

---

### Step 2: Set Cloudflare R2 Secrets in Supabase
In your terminal with Supabase CLI (or in Supabase Dashboard -> **Project Settings** -> **Edge Functions** -> **Secrets**):

```bash
supabase secrets set \
  R2_ACCOUNT_ID="your_cloudflare_account_id" \
  R2_ACCESS_KEY_ID="your_r2_access_key_id" \
  R2_SECRET_ACCESS_KEY="your_r2_secret_access_key" \
  R2_BUCKET_NAME="stories"
```

---

### Step 3: Deploy Edge Functions
Run the following commands using the Supabase CLI:

```bash
# Link your project (if not linked)
supabase link --project-ref vqgnxqabvmmpfoiceass

# Deploy all 5 Edge Functions with no JWT verification restriction for client apps
supabase functions deploy get-download-url --no-verify-jwt
supabase functions deploy get-upload-url --no-verify-jwt
supabase functions deploy create-multipart-upload --no-verify-jwt
supabase functions deploy get-part-url --no-verify-jwt
supabase functions deploy complete-multipart-upload --no-verify-jwt
```

---

### Edge Function Endpoints:
- `POST /functions/v1/get-download-url` : Generates signed presigned GET URLs for streaming videos.
- `POST /functions/v1/get-upload-url` : Generates direct single PUT presigned URLs.
- `POST /functions/v1/create-multipart-upload` : Initiates Cloudflare R2 S3 multipart upload.
- `POST /functions/v1/get-part-url` : Generates presigned PUT URL for individual 10MB chunk parts.
- `POST /functions/v1/complete-multipart-upload` : Finalizes multipart chunks in Cloudflare R2.
