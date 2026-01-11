package com.example.heicconvert;

import android.graphics.Matrix;
import android.media.ExifInterface;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.json.JSONArray;
import org.json.JSONException;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;

public class HeicConvert extends CordovaPlugin {

    private static final String TAG = "HeicConvert";

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        if ("convert".equals(action)) {
            String uriString = args.getString(0);
            int quality = args.optInt(1, 90);
            int maxSize = args.optInt(2, 0); // 기본값 0 (리사이징 안함)

            cordova.getThreadPool().execute(() -> {
                convertHeicToJpeg(uriString, quality, maxSize, callbackContext);
            });
            return true;
        }
        
        // [추가됨] checkSupport 액션
        if ("checkSupport".equals(action)) {
            // Android 9 (API 28, P) 이상이어야 HEIC 디코딩이 안정적으로 지원됨
            boolean isSupported = Build.VERSION.SDK_INT >= 28; 
            
            // boolean 값을 리턴 (JS에서는 1/0 또는 true/false로 받음)
            callbackContext.success(isSupported ? 1 : 0);
            return true;
        }        
        
        return false;
    }


private void convertHeicToJpeg(String uriString, int quality, int maxSize, CallbackContext callbackContext) {
        Context context = this.cordova.getActivity().getApplicationContext();

        if (uriString == null || uriString.isEmpty()) {
            callbackContext.error("Invalid URI");
            return;
        }

        File tempFile = null;

        try {
            Uri uri = Uri.parse(uriString);
            File localFile = null;

            Log.d(TAG, "Processing URI: " + uriString);

            // [Step 1] 파일 찾기
            String fileName = uri.getLastPathSegment();
            if (fileName != null && !fileName.isEmpty()) {
                try { fileName = URLDecoder.decode(fileName, "UTF-8"); } catch (Exception e) {}
                File internalFile = new File(context.getCacheDir(), fileName);
                if (internalFile.exists()) localFile = internalFile;

                if (localFile == null) {
                    File externalCacheDir = context.getExternalCacheDir();
                    if (externalCacheDir != null) {
                        File externalFile = new File(externalCacheDir, fileName);
                        if (externalFile.exists()) localFile = externalFile;
                    }
                }
            }

            // [Step 2] Fallback 및 원격 다운로드
            if (localFile == null) {
                if (uriString.startsWith("file://")) {
                    localFile = new File(uri.getPath());
                } else if (uriString.startsWith("http") && !uriString.contains("localhost")) {
                    tempFile = downloadToTempFile(uriString, context);
                    localFile = tempFile;
                }
            }

            if (localFile == null || !localFile.exists()) {
                callbackContext.error("File not found: " + uriString);
                return;
            }

            // =================================================================
            // [Step 3] 변환 및 저장 (BitmapFactory + EXIF Rotate + OOM 방지)
            // =================================================================
            Bitmap bitmap = null;
            FileOutputStream out = null;
            FileInputStream fis = null;

            try {
                // 3-1. 크기(Bounds)만 먼저 읽기
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                
                fis = new FileInputStream(localFile);
                BitmapFactory.decodeStream(fis, null, options);
                try { fis.close(); } catch (IOException ignored) {}

                // 파일 손상 체크
                if (options.outWidth == -1 || options.outHeight == -1) {
                    throw new IOException("Cannot decode image. File might be corrupted.");
                }

                // 3-2. 샘플 사이즈 계산 (메모리 최적화 핵심)
                options.inSampleSize = 1;
                if (maxSize > 0) {
                    options.inSampleSize = calculateSampleSize(options.outWidth, options.outHeight, maxSize, maxSize);
                } else {
                    // maxSize가 없을 때도 안전을 위해 기본값 유지 (필요 시 수정 가능)
                }

                // 3-3. 실제 디코딩
                options.inJustDecodeBounds = false;
                options.inPreferredConfig = Bitmap.Config.ARGB_8888;

                fis = new FileInputStream(localFile);
                bitmap = BitmapFactory.decodeStream(fis, null, options);
                try { fis.close(); } catch (IOException ignored) {}

                if (bitmap == null) {
                    throw new IOException("Failed to decode bitmap. Format unsupported or file corrupt.");
                }

                // =============================================================
                // [Step 3-4] EXIF 정보 읽어서 회전 보정 (생존형 로직)
                // =============================================================
                FileInputStream exifIs = null;
                try {
                    exifIs = new FileInputStream(localFile);
                    ExifInterface exif = new ExifInterface(exifIs);
                    int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                    int rotationInDegrees = 0;

                    if (orientation == ExifInterface.ORIENTATION_ROTATE_90) rotationInDegrees = 90;
                    else if (orientation == ExifInterface.ORIENTATION_ROTATE_180) rotationInDegrees = 180;
                    else if (orientation == ExifInterface.ORIENTATION_ROTATE_270) rotationInDegrees = 270;

                    if (rotationInDegrees != 0) {
                        Matrix matrix = new Matrix();
                        matrix.preRotate(rotationInDegrees);

                        try {
                            // [핵심] 회전 시도. 여기서 OOM이 나면 catch로 이동하여 원본 유지
                            Bitmap rotatedBitmap = Bitmap.createBitmap(
                                bitmap, 
                                0, 0, 
                                bitmap.getWidth(), 
                                bitmap.getHeight(), 
                                matrix, 
                                true
                            );

                            // 회전 성공 시에만 교체 및 메모리 해제
                            if (bitmap != rotatedBitmap) {
                                bitmap.recycle(); 
                                bitmap = rotatedBitmap; 
                            }
                        } catch (OutOfMemoryError rotateOOM) {
                            Log.w(TAG, "OOM during rotation. Skipping rotation to save original image.", rotateOOM);
                            // 회전을 포기하고 진행 (앱 종료 방지)
                        }
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Failed to process EXIF rotation", e);
                } finally {
                    if (exifIs != null) try { exifIs.close(); } catch (IOException ignored) {}
                }
                // =============================================================


                // 3-5. 정밀 리사이징 (옵션)
                // 회전이 적용된 bitmap을 기준으로 리사이징 수행
                if (maxSize > 0) {
                    int w = bitmap.getWidth();
                    int h = bitmap.getHeight();
                    // 회전 등으로 인해 w, h가 바뀌었을 수 있으므로 다시 체크
                    if (w > maxSize || h > maxSize) {
                        float scale = Math.min((float) maxSize / w, (float) maxSize / h);
                        int newW = Math.round(w * scale);
                        int newH = Math.round(h * scale);
                        
                        try {
                            Bitmap scaled = Bitmap.createScaledBitmap(bitmap, newW, newH, true);
                            if (scaled != bitmap) {
                                bitmap.recycle(); // 원본 해제
                                bitmap = scaled;
                            }
                        } catch (OutOfMemoryError resizeOOM) {
                            Log.w(TAG, "OOM during scaling. Using current bitmap.", resizeOOM);
                            // 리사이징 실패 시 조금 큰 상태로라도 저장 진행
                        }
                    }
                }

                // 3-6. 파일 저장
                String outputFileName = "heic_converted_" + System.currentTimeMillis() + ".jpg";
                File outFile = new File(context.getCacheDir(), outputFileName);

                out = new FileOutputStream(outFile);
                boolean compressed = bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out);
                out.flush();

                if (compressed) {
                    String resultUri = "file://" + outFile.getAbsolutePath() + "?" + System.currentTimeMillis();
                    callbackContext.success(resultUri);
                } else {
                    callbackContext.error("Failed to compress bitmap");
                }

            } catch (OutOfMemoryError oom) {
                // S24 Ultra 방어 코드: 초기 로딩 등 심각한 OOM 발생 시 처리
                Log.e(TAG, "OOM Error processing HEIC", oom);
                callbackContext.error("Out of Memory");
            } catch (Exception e) {
                callbackContext.error("Conversion error: " + e.getMessage());
                Log.e(TAG, "Conversion Logic Error", e);
            } finally {
                if (out != null) try { out.close(); } catch (IOException ignored) {}
                if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
            }

        } catch (Exception e) {
            callbackContext.error("Unexpected error: " + e.getMessage());
            Log.e(TAG, "Plugin Execute Error", e);
        } finally {
            if (tempFile != null && tempFile.exists()) {
                boolean deleted = tempFile.delete();
                if (!deleted) Log.w(TAG, "Failed to delete temp file: " + tempFile.getName());
            }
        }
    }

    // [Helper] 샘플 사이즈 계산 메서드
    private int calculateSampleSize(int width, int height, int reqWidth, int reqHeight) {
        int inSampleSize = 1;
        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }

    private File downloadToTempFile(String urlString, Context context) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlString);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            conn.connect();

            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) return null;

            File tempFile = File.createTempFile("heic_dl_", ".heic", context.getCacheDir());

            try (InputStream input = new BufferedInputStream(conn.getInputStream());
                 FileOutputStream output = new FileOutputStream(tempFile)) {
                
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = input.read(buffer)) != -1) {
                    output.write(buffer, 0, bytesRead);
                }
            }
            return tempFile;
        } catch (Exception e) {
            Log.e(TAG, "Download failed", e);
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
}