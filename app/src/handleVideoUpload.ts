import { uploadVideoToR2 } from "./uploadToR2";

// Configured Supabase Function values
export const SUPABASE_FUNCTION_URL = "https://vqgnxqabvmmpfoiceass.supabase.co/functions/v1/r2-multipart-upload";
export const SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InZxZ254cWFidm1tcGZvaWNlYXNzIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NTkzODE5NDMsImV4cCI6MjA3NDk1Nzk0M30.ZkOlMsqmfv4gCl3YG5CLe7te5DoIbZad8Y2mIpKTleA";

export async function handleFileUpload(file: File) {
  try {
    const result = await uploadVideoToR2(file, {
      supabaseFunctionUrl: SUPABASE_FUNCTION_URL,
      supabaseAnonKey: SUPABASE_ANON_KEY,
      onProgress: (pct) => {
        console.log(`Upload progress: ${pct}%`);
        // Update your progress bar here
      },
      maxParallel: 3,   // upload 3 parts at a time
      maxRetries: 5,     // retry each part up to 5 times
    });

    console.log("Upload successful!", result.url);
    return result;
  } catch (error) {
    console.error("Upload failed:", error);
    throw error;
  }
}
