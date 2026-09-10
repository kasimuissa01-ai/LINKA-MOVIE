import { serve } from "https://deno.land/std@0.177.0/http/server.ts";
import { handleCors, jsonResponse } from "../_shared/cors.ts";
import { requireAdmin } from "../_shared/auth.ts";
import { signPutUrl } from "../_shared/r2.ts";

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
    const expiresIn = body.expiresIn || 3600;

    if (!r2ObjectKey) {
      return jsonResponse({ error: "Missing required parameter: r2ObjectKey" }, 400);
    }

    // 3. Sign presigned PUT URL for Cloudflare R2 bucket `stories`
    const uploadUrl = await signPutUrl(r2ObjectKey, contentType, expiresIn);

    return jsonResponse({
      uploadUrl,
      r2ObjectKey,
      bucket: "stories",
      contentType,
      expiresIn,
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
