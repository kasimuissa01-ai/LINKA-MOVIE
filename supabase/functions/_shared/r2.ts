/**
 * Cloudflare R2 Client via aws4fetch for Supabase Edge Functions.
 * 
 * Configured specifically for bucket: `stories`
 * Lightweight S3-compatible signing without the bulky AWS SDK.
 */

import { AwsClient } from "npm:aws4fetch@1.0.20";

export function getR2Client(): {
  client: AwsClient;
  bucketUrl: string;
  bucketName: string;
} {
  const accountId = Deno.env.get("R2_ACCOUNT_ID") || "mock_account_id";
  const accessKeyId = Deno.env.get("R2_ACCESS_KEY_ID") || "mock_access_key";
  const secretAccessKey = Deno.env.get("R2_SECRET_ACCESS_KEY") || "mock_secret_key";
  const bucketName = Deno.env.get("R2_BUCKET_NAME") || "stories";

  const client = new AwsClient({
    accessKeyId,
    secretAccessKey,
    service: "s3",
    region: "auto",
  });

  const bucketUrl = `https://${accountId}.r2.cloudflarestorage.com/${bucketName}`;

  return { client, bucketUrl, bucketName };
}

/**
 * Signs a short-lived presigned GET URL for the requested R2 object in bucket `stories`.
 */
export async function signGetUrl(
  r2ObjectKey: string,
  expiresInSeconds = 3600
): Promise<string> {
  const { client, bucketUrl } = getR2Client();
  const cleanKey = r2ObjectKey.replace(/^\/+/, "");
  const requestUrl = `${bucketUrl}/${cleanKey}?X-Amz-Expires=${expiresInSeconds}`;

  const signed = await client.sign(new Request(requestUrl, { method: "GET" }), {
    aws: { signQuery: true },
  });

  return signed.url;
}

/**
 * Signs a presigned PUT URL for directly uploading an asset into bucket `stories`.
 */
export async function signPutUrl(
  r2ObjectKey: string,
  contentType = "video/mp4",
  expiresInSeconds = 3600
): Promise<string> {
  const { client, bucketUrl } = getR2Client();
  const cleanKey = r2ObjectKey.replace(/^\/+/, "");
  const requestUrl = `${bucketUrl}/${cleanKey}?X-Amz-Expires=${expiresInSeconds}`;

  const signed = await client.sign(
    new Request(requestUrl, {
      method: "PUT",
      headers: { "Content-Type": contentType },
    }),
    { aws: { signQuery: true } }
  );

  return signed.url;
}

/**
 * Initiates an S3 Multipart Upload on Cloudflare R2 bucket `stories`.
 */
export async function initiateMultipartUpload(
  r2ObjectKey: string,
  contentType = "video/mp4"
): Promise<string> {
  const { client, bucketUrl } = getR2Client();
  const cleanKey = r2ObjectKey.replace(/^\/+/, "");
  const requestUrl = `${bucketUrl}/${cleanKey}?uploads=`;

  const res = await client.fetch(requestUrl, {
    method: "POST",
    headers: { "Content-Type": contentType },
  });

  if (!res.ok) {
    const errorText = await res.text();
    throw new Error(`R2 CreateMultipartUpload failed (${res.status}): ${errorText}`);
  }

  const xmlText = await res.text();
  const match = xmlText.match(/<UploadId>(.+?)<\/UploadId>/);
  if (!match || !match[1]) {
    throw new Error("Could not extract UploadId from R2 response XML");
  }

  return match[1];
}

/**
 * Signs a presigned PUT URL for a specific part chunk of an in-progress multipart upload.
 */
export async function signPartUrl(
  r2ObjectKey: string,
  uploadId: string,
  partNumber: number,
  expiresInSeconds = 3600
): Promise<string> {
  const { client, bucketUrl } = getR2Client();
  const cleanKey = r2ObjectKey.replace(/^\/+/, "");
  const requestUrl = `${bucketUrl}/${cleanKey}?partNumber=${partNumber}&uploadId=${encodeURIComponent(
    uploadId
  )}&X-Amz-Expires=${expiresInSeconds}`;

  const signed = await client.sign(new Request(requestUrl, { method: "PUT" }), {
    aws: { signQuery: true },
  });

  return signed.url;
}

/**
 * Completes an S3 Multipart Upload by assembling parts on Cloudflare R2.
 */
export async function completeMultipartUpload(
  r2ObjectKey: string,
  uploadId: string,
  parts: { partNumber: number; etag: string }[]
): Promise<{ status: string; location: string }> {
  const { client, bucketUrl } = getR2Client();
  const cleanKey = r2ObjectKey.replace(/^\/+/, "");
  const requestUrl = `${bucketUrl}/${cleanKey}?uploadId=${encodeURIComponent(uploadId)}`;

  const sortedParts = [...parts].sort((a, b) => a.partNumber - b.partNumber);
  const partsXml = sortedParts
    .map(
      (p) =>
        `<Part><PartNumber>${p.partNumber}</PartNumber><ETag>${p.etag.replace(
          /"/g,
          ""
        )}</ETag></Part>`
    )
    .join("");

  const completePayload = `<CompleteMultipartUpload>${partsXml}</CompleteMultipartUpload>`;

  const res = await client.fetch(requestUrl, {
    method: "POST",
    headers: { "Content-Type": "application/xml" },
    body: completePayload,
  });

  if (!res.ok) {
    const errorText = await res.text();
    throw new Error(`R2 CompleteMultipartUpload failed (${res.status}): ${errorText}`);
  }

  return {
    status: "success",
    location: `${bucketUrl}/${cleanKey}`,
  };
}
