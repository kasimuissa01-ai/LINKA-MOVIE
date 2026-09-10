import { serve } from 'https://deno.land/std@0.177.0/http/server.ts';
import { UploadPartCommand } from 'npm:@aws-sdk/client-s3@3.540.0';
import { getSignedUrl } from 'npm:@aws-sdk/s3-request-presigner@3.540.0';
import { corsHeaders } from '../_shared/cors.ts';
import { getR2Client } from '../_shared/r2.ts';

serve(async (req: Request) => {
  if (req.method === 'OPTIONS') {
    return new Response('ok', { headers: corsHeaders });
  }

  try {
    const { client, bucketName } = getR2Client();
    const body = await req.json().catch(() => ({}));
    const r2ObjectKey = body.r2ObjectKey || body.key || '';
    const uploadId = body.uploadId || '';
    const partNumber = Number(body.partNumber || 1);
    const expiresIn = body.expiresIn ? Number(body.expiresIn) : 3600;

    if (!r2ObjectKey || !uploadId) {
      return new Response(
        JSON.stringify({ error: 'Missing r2ObjectKey or uploadId' }),
        { status: 400, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      );
    }

    const command = new UploadPartCommand({
      Bucket: bucketName,
      Key: r2ObjectKey,
      UploadId: uploadId,
      PartNumber: partNumber,
    });

    const partUploadUrl = await getSignedUrl(client, command, { expiresIn });

    return new Response(
      JSON.stringify({
        success: true,
        partUploadUrl,
        partNumber,
        uploadId,
        key: r2ObjectKey,
      }),
      { headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
    );
  } catch (error: any) {
    return new Response(
      JSON.stringify({ error: error.message || 'Failed to generate part upload URL' }),
      { status: 500, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
    );
  }
});
