var exec = require('cordova/exec');

var HeicConvert = {
    /**
     * HEIC 임시 파일을 JPEG로 변환 후 Blob으로 반환
     * @param {string} fileUri - file:// URI
     * @param {number} quality - JPEG 품질 (0~100)
     * @param {number} maxSize - 최대 가로/세로 크기
     * @param {function} success - 성공 콜백, 변환된 Blob 반환
     * @param {function} error - 실패 콜백
     */
    convert: function(fileUri, quality, maxSize, success, error) {
        exec(
            function(jpegPath) {
                //console.log("HEIC converted to JPEG:", jpegPath);

                // 1. Java에서 넘어온 경로에 타임스탬프 쿼리 스트링(?12345)이 있다면 제거해야 파일 시스템에서 읽을 수 있음
                var cleanPath = jpegPath;
                if (cleanPath.indexOf('?') > -1) {
                    cleanPath = cleanPath.split('?')[0];
                }

                // 2. fetch 대신 resolveLocalFileSystemURL + FileReader 사용
                window.resolveLocalFileSystemURL(cleanPath, function(fileEntry) {
                    fileEntry.file(function(file) {
                        var reader = new FileReader();

                        reader.onloadend = function() {
                            // 3. ArrayBuffer를 Blob으로 변환하여 성공 콜백 호출
                            var blob = new Blob([this.result], { type: "image/jpeg" });
                            //console.log("JPEG Blob ready via FileReader");
                            success(blob);
                        };

                        reader.onerror = function(err) {
                            console.error("FileReader failed:", err);
                            if (error) error(err);
                        };

                        // 파일을 ArrayBuffer로 읽기
                        reader.readAsArrayBuffer(file);

                    }, function(err) {
                        console.error("Failed to get file object:", err);
                        if (error) error(err);
                    });
                }, function(err) {
                    console.error("Failed to resolve local file path:", err);
                    if (error) error(err);
                });
            },
            function(err) {
                console.error("HEIC convert failed:", err);
                if (error) error(err);
            },
            "HeicConvert",
            "convert",
            [fileUri, quality || 90, maxSize || 1024]
        );
    },
    
    /**
     * [추가됨] 현재 기기가 HEIC 변환을 지원하는지 확인
     * @param {function} success - 콜백 함수 (인자: true/false)
     * @param {function} error - 에러 콜백
     */
    checkSupport: function(success, error) {
        exec(
            function(isSupported) {
                // 1(true) 또는 0(false)으로 넘어오는 경우가 많으므로 boolean으로 확실히 변환
                success(!!isSupported);
            },
            error,
            "HeicConvert",
            "checkSupport",
            []
        );
    }
};

module.exports = HeicConvert;