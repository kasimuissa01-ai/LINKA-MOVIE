import { serve } from "https://deno.land/std@0.177.0/http/server.ts";
import { handleCors, jsonResponse } from "../_shared/cors.ts";
import { requireAdmin } from "../_shared/auth.ts";
import { signPartUrl } from "../_shared/r2.ts";

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
    const partNumber = body.partNumber ? parseInt(body.partNumber, 10) : null;
    const expiresIn = body.expiresIn || 3600;

    if (!r2ObjectKey || !uploadId || !partNumber) {
      return jsonResponse(
        { error: "Missing required fields: r2ObjectKey, uploadId, and partNumber" },
        400
      );
    }

    // 3. Sign presigned PUT URL for this individual chunk on Cloudflare R2 bucket `stories`
    const partUploadUrl = await signPartUrl(
      r2ObjectKey,
      uploadId,
      partNumber,
      expiresIn
    );

    return jsonResponse({
      partUploadUrl,
      r2ObjectKey,
      uploadId,
      partNumber,
      expiresIn,
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
