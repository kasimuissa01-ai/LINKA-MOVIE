# Cloudflare R2 Presigned Multipart Upload Service (for Render)

This Node.js service generates presigned URLs for Cloudflare R2 multipart uploads.
The Android client uploads video parts directly to Cloudflare R2; video bytes never touch this service.

## Deploying on Render

### Option 1: Using a separate GitHub repository (Recommended)
1. Initialize this folder as a git repository or create a new repository on GitHub:
   ```bash
   cd render-upload-service
   git init
   git add .
   git commit -m "Initial commit for R2 upload service"
   git remote add origin https://github.com/YOUR-USERNAME/r2-upload-service.git
   git branch -M main
   git push -u origin main
   ```
2. Go to [Render Dashboard](https://dashboard.render.com).
3. Click **New +** -> **Web Service**.
4. Connect your new `r2-upload-service` repository.
5. Configure:
   - **Environment**: `Node`
   - **Build Command**: `npm install`
   - **Start Command**: `node index.js`
6. Under **Environment Variables**, add:
   - `R2_ACCOUNT_ID`: Your Cloudflare Account ID
   - `R2_ACCESS_KEY_ID`: Your Cloudflare R2 Access Key ID
   - `R2_SECRET_ACCESS_KEY`: Your Cloudflare R2 Secret Access Key
   - `R2_BUCKET`: `stories` (or your bucket name)
   - `R2_PUBLIC_DOMAIN`: (optional) your public R2 domain or custom CDN domain

---

### Option 2: Using the same Android Repository on Render
If you keep this folder inside your existing repository:
1. Commit the `render-upload-service` folder to your current repository.
2. In Render Dashboard -> Web Service -> **Settings**:
3. Set **Root Directory** to:
   ```
   render-upload-service
   ```
4. Click **Save Changes** and trigger a manual deploy.

---

## API Endpoints

- `GET /`: Health check
- `POST /create`: Initiates multipart upload, returns presigned part URLs
- `POST /complete`: Assembles uploaded parts with ETags
- `POST /abort`: Cancels multipart upload session on R2
