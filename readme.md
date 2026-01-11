# cordova-plugin-heicconvert

![Platform](https://img.shields.io/badge/platform-ios%20%7C%20android-lightgrey)
![License](https://img.shields.io/badge/license-MIT-blue)
![Version](https://img.shields.io/badge/version-1.1.0-green)

A high-performance, memory-efficient **HEIC to JPEG converter** plugin for Cordova/PhoneGap.

This plugin is engineered to handle **high-resolution HEIC images** (e.g., 108MP photos from modern smartphones) directly from the filesystem without causing **OOM (Out Of Memory)** crashes. It utilizes native frameworks (`ImageIO` on iOS, `BitmapFactory` options on Android) to resize and convert images efficiently.

> ### 🏥 Trusted by ModelDerm Project
>
> This plugin is currently deployed in the **Skin Disease Analysis Algorithm [ModelDerm (https://modelderm.com)](https://modelderm.com)**. 

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

2. Convert HEIC to JPEG (convert)
Converts a file URI to a JPEG Blob.

```JavaScript

/**
 * @param {string} uri      - The 'file://' path of the HEIC image.
 * @param {number} quality  - JPEG compression quality (0-100).
 * @param {number} maxSize  - Max width/height in pixels (0 for original size).
 * *Setting a maxSize prevents OOM on old devices.*
 * @param {function} success - Callback that receives the JPEG Blob.
 * @param {function} error   - Error callback.
 */
HeicConvert.convert(fileUri, 90, 1280, successCallback, errorCallback);
```


---

## 📄 License
MIT License