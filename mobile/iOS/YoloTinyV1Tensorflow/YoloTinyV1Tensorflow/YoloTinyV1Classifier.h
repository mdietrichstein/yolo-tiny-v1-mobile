#ifndef YoloTinyV1Classifier_h
#define YoloTinyV1Classifier_h

#import <UIKit/UIKit.h>
#import <Foundation/Foundation.h>


#ifdef __cplusplus // We'll run into problems with the bridging header if we don't guard this include
#include <tensorflow/core/public/session.h>
#endif

@class YoloV1Box;

@interface YoloTinyV1Classifier : NSObject {
    #ifdef __cplusplus
    tensorflow::Session *session;
    #endif
    
    NSMutableArray<YoloV1Box *> *boxes;
}
- (void)loadModel;
- (void) classifyImage:(nonnull CGImageRef)image;
- (void) close;
- (nonnull NSArray<YoloV1Box *> *) result;
@end

#endif
