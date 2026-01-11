#import "HeicConvert.h"
#import <ImageIO/ImageIO.h>
#import <MobileCoreServices/MobileCoreServices.h>

@implementation HeicConvert

- (void)convert:(CDVInvokedUrlCommand*)command
{
    [self.commandDelegate runInBackground:^{
        // [보완 1] 고용량 이미지 처리는 autoreleasepool로 감싸서 처리가 끝나면 즉시 메모리 해제 유도
        @autoreleasepool {
            NSString *uriString = [command.arguments objectAtIndex:0];
            NSNumber *qualityNum = [command.arguments objectAtIndex:1]; 
            NSNumber *maxSizeNum = [command.arguments objectAtIndex:2]; 

            CDVPluginResult* pluginResult = nil;

            if (!uriString || [uriString length] == 0) {
                pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"Invalid URI"];
                [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
                return;
            }

            // 1. 파일 경로 처리
            NSString *filePath = uriString;
            if ([uriString hasPrefix:@"file://"]) {
                filePath = [uriString substringFromIndex:7];
            }
            filePath = [filePath stringByRemovingPercentEncoding];

            // 파일 존재 확인
            if (![[NSFileManager defaultManager] fileExistsAtPath:filePath]) {
                pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"File not found"];
                [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
                return;
            }

            NSURL *fileURL = [NSURL fileURLWithPath:filePath];

            // 2. ImageIO 소스 생성
            CGImageSourceRef imageSource = CGImageSourceCreateWithURL((__bridge CFURLRef)fileURL, NULL);
            if (!imageSource) {
                pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"Failed to create image source"];
                [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
                return;
            }

            // 3. 변환 옵션 설정
            int maxSize = [maxSizeNum intValue];
            NSMutableDictionary *options = [NSMutableDictionary dictionary];
            
            [options setObject:@YES forKey:(id)kCGImageSourceCreateThumbnailFromImageAlways];
            [options setObject:@YES forKey:(id)kCGImageSourceCreateThumbnailWithTransform];
            [options setObject:@NO forKey:(id)kCGImageSourceShouldCache];

            if (maxSize > 0) {
                [options setObject:@(maxSize) forKey:(id)kCGImageSourceThumbnailMaxPixelSize];
            }

            // 4. 이미지 생성 (Downsampling & Rotate)
            CGImageRef scaledImageRef = CGImageSourceCreateThumbnailAtIndex(imageSource, 0, (__bridge CFDictionaryRef)options);
            
            if (scaledImageRef) {
                UIImage *finalImage = [UIImage imageWithCGImage:scaledImageRef];
                
                // [보완 2] Quality 값 범위 안전장치 (0.0 ~ 1.0)
                float qualityVal = [qualityNum floatValue] / 100.0;
                if (qualityVal < 0.0) qualityVal = 0.5;
                if (qualityVal > 1.0) qualityVal = 1.0;

                NSData *jpegData = UIImageJPEGRepresentation(finalImage, qualityVal);
                
                if (jpegData) {
                    // 5. 임시 파일 저장
                    NSString *fileName = [NSString stringWithFormat:@"heic_converted_%f.jpg", [[NSDate date] timeIntervalSince1970]];
                    NSString *tempPath = [NSTemporaryDirectory() stringByAppendingPathComponent:fileName];
                    
                    // atomically:YES는 임시 파일에 먼저 쓰고 완료되면 이름을 바꾸므로 파일 손상 방지에 좋음
                    BOOL success = [jpegData writeToFile:tempPath atomically:YES];
                    
                    if (success) {
                        NSString *resultUri = [NSString stringWithFormat:@"file://%@", tempPath];
                        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:resultUri];
                    } else {
                        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"Failed to write file"];
                    }
                } else {
                    pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"Failed to compress to JPEG"];
                }
                
                // CG 객체 해제
                CFRelease(scaledImageRef);
            } else {
                pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"Failed to create thumbnail"];
            }

            // Source 객체 해제
            if (imageSource) CFRelease(imageSource);

            [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
        } // @autoreleasepool end
    }];
}

// [추가됨] 지원 여부 체크 함수
- (void)checkSupport:(CDVInvokedUrlCommand*)command
{
    BOOL isSupported = NO;

    // iOS 11.0 이상인지 체크 (HEIC는 iOS 11부터 도입됨)
    if (@available(iOS 11.0, *)) {
        isSupported = YES;
    }

    CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsBool:isSupported];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}

@end