import { serve } from 'https://deno.land/std@0.177.0/http/server.ts';
import { CreateMultipartUploadCommand } from 'npm:@aws-sdk/client-s3@3.540.0';
import { corsHeaders } from '../_shared/cors.ts';
import { getR2Client } from '../_shared/r2.ts';

serve(async (req: Request) => {
  if (req.method === 'OPTIONS') {
    return new Response('ok', { headers: corsHeaders });
  }

  try {
    const { client, bucketName } = getR2Client();
    const body = await req.json().catch(() => ({}));
    const r2ObjectKey = body.r2ObjectKey || body.key || `movies/${Date.now()}.mp4`;
    const contentType = body.contentType || 'video/mp4';

    const command = new CreateMultipartUploadCommand({
      Bucket: bucketName,
      Key: r2ObjectKey,
      ContentType: contentType,
    });

    const response = await client.send(command);

    return new Response(
      JSON.stringify({
        success: true,
        uploadId: response.UploadId,
        key: r2ObjectKey,
        bucket: bucketName,
      }),
      { headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
    );
  } catch (error: any) {
    return new Response(
      JSON.stringify({ error: error.message || 'Failed to initiate multipart upload' }),
      { status: 500, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
    );
  }
});
