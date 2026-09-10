import { serve } from "https://deno.land/std@0.177.0/http/server.ts";
import { handleCors, jsonResponse } from "../_shared/cors.ts";
import { requireAdmin } from "../_shared/auth.ts";
import { completeMultipartUpload } from "../_shared/r2.ts";

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
    const uploadId = body.uploadId;
    const parts = body.parts; // Array of { partNumber: number, etag: string }

    if (!r2ObjectKey || !uploadId || !Array.isArray(parts)) {
      return jsonResponse(
        { error: "Missing required fields: r2ObjectKey, uploadId, and parts array" },
        400
      );
    }

    // 3. Complete multipart upload on Cloudflare R2 bucket `stories`
    const result = await completeMultipartUpload(r2ObjectKey, uploadId, parts);

    return jsonResponse({
      status: "success",
      r2ObjectKey,
      uploadId,
      location: result.location,
      bucket: "stories",
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
