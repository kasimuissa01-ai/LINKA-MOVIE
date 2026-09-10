import { serve } from 'https://deno.land/std@0.177.0/http/server.ts';
import { GetObjectCommand } from 'npm:@aws-sdk/client-s3@3.540.0';
import { getSignedUrl } from 'npm:@aws-sdk/s3-request-presigner@3.540.0';
import { corsHeaders } from '../_shared/cors.ts';
import { getR2Client } from '../_shared/r2.ts';

serve(async (req: Request) => {
  if (req.method === 'OPTIONS') {
    return new Response('ok', { headers: corsHeaders });
  }

  try {
    const { client, bucketName } = getR2Client();
    let r2ObjectKey = '';
    let expiresIn = 3600;

    if (req.method === 'POST') {
      const body = await req.json().catch(() => ({}));
      r2ObjectKey = body.r2ObjectKey || body.key || '';
      if (body.expiresIn) expiresIn = Number(body.expiresIn);
    } else {
      const url = new URL(req.url);
      r2ObjectKey = url.searchParams.get('r2ObjectKey') || url.searchParams.get('key') || '';
    }

    if (!r2ObjectKey) {
      return new Response(
        JSON.stringify({ error: 'Missing r2ObjectKey parameter' }),
        { status: 400, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      );
    }

    const command = new GetObjectCommand({
      Bucket: bucketName,
      Key: r2ObjectKey,
    });

    const downloadUrl = await getSignedUrl(client, command, { expiresIn });

    return new Response(
      JSON.stringify({ success: true, downloadUrl, key: r2ObjectKey, expiresIn }),
      { headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
    );
  } catch (error: any) {
    return new Response(
      JSON.stringify({ error: error.message || 'Internal server error' }),
      { status: 500, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
    );
  }
});
