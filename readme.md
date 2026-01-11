# cordova-plugin-heicconvert

![Platform](https://img.shields.io/badge/platform-ios%20%7C%20android-lightgrey)
![License](https://img.shields.io/badge/license-MIT-blue)
![Version](https://img.shields.io/badge/version-1.1.0-green)

A high-performance, memory-efficient **HEIC to JPEG converter** plugin for Cordova.

This plugin is engineered to handle **high-resolution HEIC images** (e.g., 108MP photos from modern smartphones) directly from the filesystem without causing **OOM (Out Of Memory)** crashes. It utilizes native frameworks (`ImageIO` on iOS, `BitmapFactory` options on Android) to resize and convert images efficiently. This plugin is currently deployed in the **Skin Disease Analysis Algorithm [ModelDerm (https://modelderm.com)](https://modelderm.com)**. 

---

## 📱 Supported Platforms

| Platform | Minimum Version | Note |
| :--- | :--- | :--- |
| **iOS** | **iOS 11.0+** | Native HEIC support requires iOS 11. |
| **Android** | **API Level 28+** (Android 9.0) | Recommended for native decoding stability. |

---

## 📖 API Usage

1. Check Support 

```JavaScript

HeicConvert.checkSupport(
    function(isSupported) {
        if (isSupported) {
            console.log("✅ HEIC conversion is supported.");
        } else {
            console.warn("❌ HEIC is not supported on this OS version.");
        }
    },
    function(error) {
        console.error("Error checking support:", error);
    }
);
```

2. An Example in ModelDerm Project

Please modify the code with the assistance of coding AI.

```JavaScript

const file = fileInput.files[0];

if (!cordova.file || !cordova.file.cacheDirectory) {
    console_error("cordova.file.cacheDirectory is undefined. Ensure cordova-plugin-file is installed.");
    setCookie2('error_native_heic',getToday());
    this.onImageError(h.ERRORS.HEIC_FAIL);
    return;
}

const cacheDir = cordova.file.cacheDirectory;

// [수정] function(dirEntry) -> (dirEntry) =>
window.resolveLocalFileSystemURL(cacheDir, (dirEntry) => {
    
    // [수정] function() -> () =>
    (async () => {
        try {
            //console.log("Original file:", file.name, file.type, file.size);

            // 1. Blob 생성
            const arrayBuffer = await file.arrayBuffer();
            //console.log("ArrayBuffer length:", arrayBuffer.byteLength);
            const blob = new Blob([arrayBuffer], { type: file.type });
            //console.log("Blob created:", blob.type, blob.size);

            // 2. 임시 파일 생성
            const tempFileName = "temp_heic_" + Date.now() + ".heic";

            // [수정] function(fileEntry) -> (fileEntry) =>
            dirEntry.getFile(tempFileName, { create: true, exclusive: false }, (fileEntry) => {
                //console.log("FileEntry created:", tempFileName);

                // [수정] function(fileWriter) -> (fileWriter) =>
                fileEntry.createWriter((fileWriter) => {
                    
                    // [수정] function() -> () => 
                    // 이렇게 해야 내부에서 this.onImageError 접근 가능
                    fileWriter.onwriteend = () => {
                        try {
                            // ★ 반드시 toNativeURL() 사용
                            const nativePath = fileEntry.toURL();
                            //console.log("Temporary HEIC file written, native path:", nativePath);

                            // 3. HEIC 변환 호출
                            HeicConvert.convert(
                                nativePath, // file:// URI
                                99,         // JPEG quality
                                MAX_RES,    // 최대 크기
                                (jpegBlob) => { // [수정] 화살표 함수
                                    
                                    //console.log("HEIC converted successfully. Blob size:", jpegBlob.size);
                                    
                                    // 바로 이미지 로딩 함수에 Blob을 넘기면 됩니다.
                                    loadByBlobUrl(jpegBlob);
                                },
                                (err) => { // [수정] 화살표 함수
                                    console_error("HEIC native convert failed:", err);
                                    setCookie2('error_native_heic',getToday());
                                    // 이제 this가 정상적으로 유지됨
                                    this.onImageError(h.ERRORS.HEIC_FAIL);
                                }
                            );

                        } catch (err) {
                            console_error("Error after writing temp file:", err);
                            setCookie2('error_native_heic',getToday());
                            this.onImageError(h.ERRORS.HEIC_FAIL);
                        }
                    };

                    // [수정] function(err) -> (err) =>
                    fileWriter.onerror = (err) => {
                        console_error("Failed to write temporary HEIC file", err);
                        setCookie2('error_native_heic',getToday());
                        this.onImageError(h.ERRORS.HEIC_FAIL);
                    };

                    //console.log("Writing Blob to temporary file...");
                    fileWriter.write(blob);

                }, (err) => { // [수정] createWriter error callback
                    console_error("Failed to create file writer", err);
                    setCookie2('error_native_heic',getToday());
                    this.onImageError(h.ERRORS.HEIC_FAIL);
                });

            }, (err) => { // [수정] getFile error callback
                console_error("Failed to create temporary file", err);
                setCookie2('error_native_heic',getToday());
                this.onImageError(h.ERRORS.HEIC_FAIL);
            });

        } catch (err) {
            console_error("Unexpected error in async IIFE:", err);
            setCookie2('error_native_heic',getToday());
            this.onImageError(h.ERRORS.HEIC_FAIL);
        }
    })();
}, (err) => { // [수정] resolveLocalFileSystemURL error callback
    console_error("Failed to access cache directory", err);
    setCookie2('error_native_heic',getToday());
    this.onImageError(h.ERRORS.HEIC_FAIL);
});
```


---

## 📄 License
> MIT License
> 
> Please mention that this is the plugin used at Model Dermatology (https://modelderm.com).