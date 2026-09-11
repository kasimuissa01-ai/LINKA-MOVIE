const express = require("express");
const cors = require("cors");
const {
  S3Client,
  CreateMultipartUploadCommand,
  UploadPartCommand,
  CompleteMultipartUploadCommand,
  AbortMultipartUploadCommand,
} = require("@aws-sdk/client-s3");
const { getSignedUrl } = require("@aws-sdk/s3-request-presigner");
require("dotenv").config();

const app = express();

app.use(cors());
app.use(express.json());

const R2_ACCOUNT_ID = process.env.R2_ACCOUNT_ID;
const R2_ACCESS_KEY_ID = process.env.R2_ACCESS_KEY_ID;
const R2_SECRET_ACCESS_KEY = process.env.R2_SECRET_ACCESS_KEY;
const R2_BUCKET = process.env.R2_BUCKET || "stories";
const PART_SIZE = parseInt(process.env.PART_SIZE || "52428800", 10); // 50 MB
const URL_EXPIRY = parseInt(process.env.URL_EXPIRY || "7200", 10); // 2 hours

if (!R2_ACCOUNT_ID || !R2_ACCESS_KEY_ID || !R2_SECRET_ACCESS_KEY) {
  console.warn(
    "⚠️ Warning: Missing R2 environment variables (R2_ACCOUNT_ID, R2_ACCESS_KEY_ID, R2_SECRET_ACCESS_KEY)."
  );
}

const s3 = new S3Client({
  region: "auto",
  endpoint: `https://${R2_ACCOUNT_ID}.r2.cloudflarestorage.com`,
  credentials: {
    accessKeyId: R2_ACCESS_KEY_ID || "",
    secretAccessKey: R2_SECRET_ACCESS_KEY || "",
  },
});

// Health check endpoint
app.get("/", (req, res) => {
  res.json({
    status: "ok",
    service: "Cloudflare R2 Presigned Multipart Upload Service",
    bucket: R2_BUCKET,
    partSizeMb: Math.round(PART_SIZE / (1024 * 1024)),
  });
});

/**
 * 1. POST /create
 * Body: { filename, contentType, fileSize }
 * Returns: { uploadId, key, partSize, partCount, parts: [{ partNumber, url }] }
 */
app.post("/create", async (req, res) => {
  try {
    const { filename, contentType, fileSize } = req.body;
    if (!filename || !fileSize) {
      return res.status(400).json({ error: "Missing filename or fileSize" });
    }

    const sanitizedFilename = filename.replace(/[^a-zA-Z0-9._-]/g, "_");
    const key = `videos/${Date.now()}-${sanitizedFilename}`;

    const createCommand = new CreateMultipartUploadCommand({
      Bucket: R2_BUCKET,
      Key: key,
      ContentType: contentType || "video/mp4",
    });

    const createResponse = await s3.send(createCommand);
    const uploadId = createResponse.UploadId;

    const partCount = Math.ceil(fileSize / PART_SIZE) || 1;
    const parts = [];

    for (let partNumber = 1; partNumber <= partCount; partNumber++) {
      const uploadPartCommand = new UploadPartCommand({
        Bucket: R2_BUCKET,
        Key: key,
        UploadId: uploadId,
        PartNumber: partNumber,
      });

      const presignedUrl = await getSignedUrl(s3, uploadPartCommand, {
        expiresIn: URL_EXPIRY,
      });

      parts.push({
        partNumber,
        url: presignedUrl,
      });
    }

    console.log(
      `Initiated uploadId: ${uploadId} for key: ${key} (${partCount} parts)`
    );

    res.json({
      uploadId,
      key,
      partSize: PART_SIZE,
      partCount,
      parts,
    });
  } catch (error) {
    console.error("Error in /create:", error);
    res.status(500).json({ error: error.message || "Failed to initiate multipart upload" });
  }
});

/**
 * 2. POST /complete
 * Body: { key, uploadId, parts / etags: [{ partNumber, etag }] }
 * Returns: { success: true, key, url }
 */
app.post("/complete", async (req, res) => {
  try {
    const { key, uploadId, parts, etags } = req.body;
    const partsList = parts || etags;

    if (!key || !uploadId || !Array.isArray(partsList) || partsList.length === 0) {
      return res.status(400).json({
        error: "Missing required fields: key, uploadId, or parts/etags array",
      });
    }

    const formattedParts = partsList.map((p) => ({
      PartNumber: p.partNumber || p.PartNumber,
      ETag: p.etag || p.ETag,
    }));

    // S3 requires parts to be sorted by PartNumber ascending
    formattedParts.sort((a, b) => a.PartNumber - b.PartNumber);

    const completeCommand = new CompleteMultipartUploadCommand({
      Bucket: R2_BUCKET,
      Key: key,
      UploadId: uploadId,
      MultipartUpload: {
        Parts: formattedParts,
      },
    });

    const completeResponse = await s3.send(completeCommand);
    console.log(`Completed upload for key: ${key}`);

    const publicUrl = process.env.R2_PUBLIC_DOMAIN
      ? `https://${process.env.R2_PUBLIC_DOMAIN}/${key}`
      : `https://${R2_ACCOUNT_ID}.r2.cloudflarestorage.com/${R2_BUCKET}/${key}`;

    res.json({
      success: true,
      key,
      url: publicUrl,
      location: completeResponse.Location || publicUrl,
    });
  } catch (error) {
    console.error("Error in /complete:", error);
    res.status(500).json({ error: error.message || "Failed to complete multipart upload" });
  }
});

/**
 * 3. POST /abort
 * Body: { key, uploadId }
 * Returns: { success: true, message }
 */
app.post("/abort", async (req, res) => {
  try {
    const { key, uploadId } = req.body;
    if (!key || !uploadId) {
      return res.status(400).json({ error: "Missing key or uploadId" });
    }

    const abortCommand = new AbortMultipartUploadCommand({
      Bucket: R2_BUCKET,
      Key: key,
      UploadId: uploadId,
    });

    await s3.send(abortCommand);
    console.log(`Aborted multipart upload for key: ${key}, uploadId: ${uploadId}`);

    res.json({
      success: true,
      message: "Multipart upload successfully aborted",
    });
  } catch (error) {
    console.error("Error in /abort:", error);
    res.status(500).json({ error: error.message || "Failed to abort multipart upload" });
  }
});

const PORT = process.env.PORT || 3000;
app.listen(PORT, () => {
  console.log(`R2 Upload Service listening on port ${PORT}`);
});
