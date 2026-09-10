import { serve } from 'https://deno.land/std@0.177.0/http/server.ts';
import { CompleteMultipartUploadCommand } from 'npm:@aws-sdk/client-s3@3.540.0';
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
    const parts = body.parts || []; // Array of { partNumber: 1, etag: 'string' }

    if (!r2ObjectKey || !uploadId) {
      return new Response(
        JSON.stringify({ error: 'Missing r2ObjectKey or uploadId' }),
        { status: 400, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      );
    }

    const formattedParts = parts.map((p: any) => ({
      PartNumber: Number(p.partNumber || p.PartNumber),
      ETag: p.etag ? (p.etag.startsWith('"') ? p.etag : `"${p.etag}"`) : undefined,
    }));

    const command = new CompleteMultipartUploadCommand({
      Bucket: bucketName,
      Key: r2ObjectKey,
      UploadId: uploadId,
      MultipartUpload: {
        Parts: formattedParts,
      },
    });

    const response = await client.send(command);

    return new Response(
      JSON.stringify({
        success: true,
        location: response.Location,
        key: r2ObjectKey,
        bucket: bucketName,
      }),
      { headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
    );
  } catch (error: any) {
    return new Response(
      JSON.stringify({ error: error.message || 'Failed to complete multipart upload' }),
      { status: 500, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
    );
  }
});
