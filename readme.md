# cordova-plugin-heicconvert

![Platform](https://img.shields.io/badge/platform-ios%20%7C%20android-lightgrey)
![License](https://img.shields.io/badge/license-MIT-blue)
![Version](https://img.shields.io/badge/version-1.1.0-green)

A high-performance, memory-efficient **HEIC to JPEG converter** plugin for Cordova/PhoneGap.

This plugin is engineered to handle **high-resolution HEIC images** (e.g., 108MP photos from modern smartphones) directly from the filesystem without causing **OOM (Out Of Memory)** crashes. It utilizes native frameworks (`ImageIO` on iOS, `BitmapFactory` options on Android) to resize and convert images efficiently.

> ### 🏥 Trusted by ModelDerm Project
>
> This plugin is currently deployed in the **Skin Disease Analysis Algorithm [ModelDerm; https://modelderm.com](https://modelderm.com)**. 

---

## 🚀 Key Features

* **🛡️ Memory Safe:** Prevents app crashes by using downsampling during decoding, avoiding the need to load huge raw bitmaps into RAM.
* **⚡ Native Performance:**
    * **iOS:** Leverages the `ImageIO` framework.
    * **Android:** Utilizes `BitmapFactory` & native HEIC decoders.

---

## 📱 Supported Platforms

| Platform | Minimum Version | Note |
| :--- | :--- | :--- |
| **iOS** | **iOS 11.0+** | Native HEIC support requires iOS 11. |
| **Android** | **API Level 28+** (Android 9.0) | Recommended for native decoding stability. |

---

## 📦 Installation

Install the plugin directly from the GitHub repository:

```bash
cordova plugin add [https://github.com/whria78/cordova-plugin-heicconvert.git](https://github.com/whria78/cordova-plugin-heicconvert.git)
```

## 📖 API Usage
The plugin exposes a global object HeicConvert.

1. Check Support (checkSupport)
Before attempting conversion, it is highly recommended to check if the device supports HEIC operations.

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
## 💻 Full Example
Here is a practical example of picking an image (e.g., using cordova-plugin-camera) and converting it only if it is an HEIC file.

```JavaScript

function processImage(imageUri) {
    // 1. Check if the file extension is .heic
    var isHeic = imageUri.toLowerCase().endsWith(".heic");

    if (isHeic) {
        // 2. Check device capability
        HeicConvert.checkSupport(function(isSupported) {
            if (!isSupported) {
                alert("HEIC conversion is not supported on this device.");
                return;
            }

            console.log("🔄 Converting HEIC...");
            
            // 3. Convert: Quality 80, Resize to max 1024px (Safe for most UI)
            HeicConvert.convert(imageUri, 80, 1024, function(blob) {
                
                // Success: Create URL for display or upload the blob
                var url = URL.createObjectURL(blob);
                var imgElement = document.getElementById("myImage");
                imgElement.src = url;
                
                console.log("✅ Conversion successful! Blob size:", blob.size);

            }, function(err) {
                console.error("❌ Conversion failed:", err);
            });
        }, function(err) {
             console.error("Check support error:", err);
        });
    } else {
        // Handle regular images (JPG, PNG) normally
        document.getElementById("myImage").src = imageUri;
    }
}
```

## 🛠 Technical Details

### Why use this plugin?
Standard methods of loading images (like `UIImage` in iOS or plain `BitmapFactory.decodeFile` in Android) often attempt to load the **entire image into memory** before resizing. For a 108MP HEIC photo, this can require **hundreds of megabytes of RAM**, causing the OS to kill the app immediately (OOM Crash).

#### iOS Implementation
Uses **`ImageIO`** (`CGImageSourceCreateThumbnailAtIndex` with `kCGImageSourceThumbnailMaxPixelSize`).

* **Benefit:** The OS decodes only the pixels strictly needed for the target size directly from the file stream.
* **Result:** Memory usage drops significantly (e.g., from **~400MB** for a full load to **~10MB** for a thumbnail).

#### Android Implementation
Uses **`BitmapFactory.Options.inSampleSize`**.

* **Benefit:** Calculates the optimal power-of-2 downsampling rate before decoding the pixel data.
* **Result:** Prevents `OutOfMemoryError` and efficiently handles rotation parsing via `ExifInterface`.

---

## 📄 License
MIT License