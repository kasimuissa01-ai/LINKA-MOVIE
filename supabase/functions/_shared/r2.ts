import { S3Client } from 'npm:@aws-sdk/client-s3@3.540.0';

export function getR2Client(): { client: S3Client; bucketName: string } {
  const accountId = Deno.env.get('R2_ACCOUNT_ID') ?? Deno.env.get('CLOUDFLARE_ACCOUNT_ID') ?? '';
  const accessKeyId = Deno.env.get('R2_ACCESS_KEY_ID') ?? '';
  const secretAccessKey = Deno.env.get('R2_SECRET_ACCESS_KEY') ?? '';
  const bucketName = Deno.env.get('R2_BUCKET_NAME') ?? 'stories';

  const client = new S3Client({
    region: 'auto',
    endpoint: `https://${accountId}.r2.cloudflarestorage.com`,
    credentials: {
      accessKeyId,
      secretAccessKey,
    },
  });

  return { client, bucketName };
}
