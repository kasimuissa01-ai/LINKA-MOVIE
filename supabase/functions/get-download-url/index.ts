import { serve } from "https://deno.land/std@0.177.0/http/server.ts";
import { handleCors, jsonResponse } from "../_shared/cors.ts";
import { authenticate } from "../_shared/auth.ts";
import { signGetUrl } from "../_shared/r2.ts";

serve(async (req) => {
  const corsResponse = handleCors(req);
  if (corsResponse) return corsResponse;

  if (req.method !== "POST" && req.method !== "GET") {
    return jsonResponse({ error: "Method not allowed" }, 405);
  }

  try {
    // 1. Verify Firebase Auth ID token (Any authenticated user may call this)
    const user = await authenticate(req);

    // 2. Extract r2ObjectKey from request
    let r2ObjectKey: string | null = null;
    let expiresIn = 3600; // 60 minutes TTL

    if (req.method === "POST") {
      const body = await req.json().catch(() => ({}));
      r2ObjectKey = body.r2ObjectKey || body.key;
      if (body.expiresIn) expiresIn = body.expiresIn;
    } else {
      const url = new URL(req.url);
      r2ObjectKey = url.searchParams.get("r2ObjectKey") || url.searchParams.get("key");
      const expParam = url.searchParams.get("expiresIn");
      if (expParam) expiresIn = parseInt(expParam, 10);
    }

    if (!r2ObjectKey) {
      return jsonResponse({ error: "Missing required parameter: r2ObjectKey" }, 400);
    }

    // 3. Sign short-lived presigned GET URL for Cloudflare R2 bucket `stories`
    const downloadUrl = await signGetUrl(r2ObjectKey, expiresIn);

    return jsonResponse({
      downloadUrl,
      r2ObjectKey,
      expiresIn,
      bucket: "stories",
      requestedBy: user.uid,
    });
  } catch (err) {
    const message = (err as Error).message;
    const status = message.includes("token") || message.includes("Authorization") ? 401 : 500;
    return jsonResponse({ error: message }, status);
  }
});
