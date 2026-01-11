#import <Cordova/CDV.h>

@interface HeicConvert : CDVPlugin

- (void)convert:(CDVInvokedUrlCommand*)command;
// [추가됨]
- (void)checkSupport:(CDVInvokedUrlCommand*)command;

@end