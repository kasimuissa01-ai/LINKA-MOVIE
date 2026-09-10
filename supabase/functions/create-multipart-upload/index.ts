import { serve } from "https://deno.land/std@0.177.0/http/server.ts";
import { handleCors, jsonResponse } from "../_shared/cors.ts";
import { requireAdmin } from "../_shared/auth.ts";
import { initiateMultipartUpload } from "../_shared/r2.ts";

serve(async (req) => {
  const corsResponse = handleCors(req);
  if (corsResponse) return corsResponse;

  if (req.method !== "POST") {
    return jsonResponse({ error: "Method not allowed. Use POST." }, 405);
  }

  try {
    // 1. Authenticate and enforce role == 'admin' from Firestore
    const adminUser = await requireAdmin(req);

    // 2. Parse payload
    const body = await req.json().catch(() => ({}));
    const r2ObjectKey = body.r2ObjectKey || body.key;
    const contentType = body.contentType || "video/mp4";

    if (!r2ObjectKey) {
      return jsonResponse({ error: "Missing required parameter: r2ObjectKey" }, 400);
    }

    // 3. Initiate multipart upload on Cloudflare R2 bucket `stories`
    const uploadId = await initiateMultipartUpload(r2ObjectKey, contentType);

    return jsonResponse({
      uploadId,
      r2ObjectKey,
      bucket: "stories",
      partSizeRecommendedBytes: 10 * 1024 * 1024, // 10MB chunks
      authorizedAdmin: adminUser.uid,
    });
  } catch (err) {
    const message = (err as Error).message;
    const status = message.includes("Forbidden")
      ? 403
      : message.includes("token") || message.includes("Authorization")
      ? 401
      : 500;
    return jsonResponse({ error: message }, status);
  }
});
