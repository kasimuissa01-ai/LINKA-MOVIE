/**
 * App-side: Direct-to-R2 Multipart Upload
 *
 * Works in browser and React Native.
 */

export interface UploadOptions {
  /** Your Supabase Edge Function URL */
  supabaseFunctionUrl: string;
  /** Your Supabase anon/public key */
  supabaseAnonKey: string;
  /** Progress callback (0–100) */
  onProgress?: (percent: number) => void;
  /** Max parallel part uploads (default: 3) */
  maxParallel?: number;
  /** Max retries per part on failure (default: 5) */
  maxRetries?: number;
}

export interface UploadResult {
  key: string;
  url: string;
}

/**
 * Upload a large video file directly to R2 via multipart upload.
 * The Supabase function only generates presigned URLs — the actual
 * file data goes directly from the app to R2, bypassing Supabase entirely.
 */
export async function uploadVideoToR2(
  file: File | Blob,
  options: UploadOptions
): Promise<UploadResult> {
  const {
    supabaseFunctionUrl,
    supabaseAnonKey,
    onProgress,
    maxParallel = 3,
    maxRetries = 5,
  } = options;

  const filename = file instanceof File ? file.name : `upload-${Date.now()}.mp4`;
  const contentType = file.type || "video/mp4";
  const fileSize = file.size;

  if (fileSize === 0) {
    throw new Error("File is empty");
  }

  console.log(`Starting multipart upload: ${filename} (${(fileSize / 1024 / 1024 / 1024).toFixed(2)} GB)`);

  // ── Step 1: Ask Supabase function to create multipart upload + get presigned URLs ──
  const createRes = await fetch(supabaseFunctionUrl, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${supabaseAnonKey}`,
      apikey: supabaseAnonKey,
    },
    body: JSON.stringify({
      action: "create",
      filename,
      contentType,
      fileSize,
    }),
  });

  if (!createRes.ok) {
    const err = await createRes.json().catch(() => ({ error: "Unknown error" }));
    throw new Error(`Failed to create upload: ${err.error || createRes.statusText}`);
  }

  const { uploadId, key, partSize, partCount, parts } = await createRes.json();

  console.log(`Upload created: ${partCount} parts, ${(partSize / 1024 / 1024).toFixed(0)} MB each`);

  // ── Step 2: Upload each part directly to R2 using presigned URLs ──
  const etags: { PartNumber: number; ETag: string }[] = new Array(partCount);
  let completedParts = 0;

  // Upload a single part with retry logic
  async function uploadPart(partInfo: { partNumber: number; url: string }) {
    const partIndex = partInfo.partNumber - 1;
    const start = partIndex * partSize;
    const end = Math.min(start + partSize, fileSize);
    const chunk = file.slice(start, end);

    let lastErr: Error | null = null;

    for (let attempt = 1; attempt <= maxRetries; attempt++) {
      try {
        const res = await fetch(partInfo.url, {
          method: "PUT",
          body: chunk,
          headers: { "Content-Type": "application/octet-stream" },
        });

        if (!res.ok) {
          throw new Error(`Part ${partInfo.partNumber} upload failed: HTTP ${res.status}`);
        }

        const etag = res.headers.get("ETag");
        if (!etag) {
          throw new Error(`Part ${partInfo.partNumber}: missing ETag header`);
        }

        etags[partIndex] = { PartNumber: partInfo.partNumber, ETag: etag };
        completedParts++;

        if (onProgress) {
          const pct = Math.round((completedParts / partCount) * 100);
          onProgress(pct);
        }

        console.log(`Part ${partInfo.partNumber}/${partCount} uploaded ✓`);
        return;
      } catch (err) {
        lastErr = err as Error;
        console.warn(`Part ${partInfo.partNumber} attempt ${attempt}/${maxRetries} failed: ${lastErr.message}`);

        if (attempt < maxRetries) {
          // Exponential backoff: 1s, 2s, 4s, 8s, 16s
          const delay = Math.pow(2, attempt - 1) * 1000;
          await new Promise((r) => setTimeout(r, delay));
        }
      }
    }

    throw new Error(`Part ${partInfo.partNumber} failed after ${maxRetries} retries: ${lastErr?.message}`);
  }

  // Run uploads with limited parallelism
  const queue = [...parts];
  const inFlight: Promise<void>[] = [];

  while (queue.length > 0 || inFlight.length > 0) {
    while (inFlight.length < maxParallel && queue.length > 0) {
      const part = queue.shift()!;
      inFlight.push(uploadPart(part));
    }

    // Wait for at least one to finish
    await Promise.race(inFlight);

    // Remove completed promises
    for (let i = inFlight.length - 1; i >= 0; i--) {
      const settled = await Promise.race([inFlight[i].then(() => true), Promise.resolve(false)]);
      if (settled) {
        inFlight.splice(i, 1);
      }
    }
  }

  // Make sure all are done
  await Promise.all(inFlight);

  console.log("All parts uploaded. Completing multipart upload...");

  // ── Step 3: Tell Supabase function to complete the upload ──
  const completeRes = await fetch(supabaseFunctionUrl, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${supabaseAnonKey}`,
      apikey: supabaseAnonKey,
    },
    body: JSON.stringify({
      action: "complete",
      key,
      uploadId,
      etags,
    }),
  });

  if (!completeRes.ok) {
    const err = await completeRes.json().catch(() => ({ error: "Unknown error" }));
    throw new Error(`Failed to complete upload: ${err.error || completeRes.statusText}`);
  }

  const result = await completeRes.json();
  console.log("Upload complete!", result.url);

  return { key: result.key, url: result.url };
}

/**
 * Abort a failed multipart upload and clean up partial parts on R2.
 * Call this if uploadVideoToR2 throws an error.
 */
export async function abortR2Upload(
  supabaseFunctionUrl: string,
  supabaseAnonKey: string,
  key: string,
  uploadId: string
): Promise<void> {
  await fetch(supabaseFunctionUrl, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${supabaseAnonKey}`,
      apikey: supabaseAnonKey,
    },
    body: JSON.stringify({ action: "abort", key, uploadId }),
  });
  console.log("Upload aborted, partial parts cleaned up.");
}
