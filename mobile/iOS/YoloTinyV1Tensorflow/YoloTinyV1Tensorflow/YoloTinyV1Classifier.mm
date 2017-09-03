#include "YoloTinyV1Classifier.h"

#include <tensorflow/core/public/session.h>
#include <tensorflow/core/platform/env.h>

#include <AVFoundation/AVFoundation.h>
#include <CoreText/CoreText.h>
#include "YoloTinyV1Tensorflow-Swift.h"

namespace tf = tensorflow;

const std::string inputTensorName = "image_input:0";
const std::string outputTensorName = "prediction/BiasAdd:0";

const NSUInteger imageDimension = 448;
const NSUInteger numClasses = 20;
const NSUInteger gridSize = 7;
const NSUInteger numBoxesPerCell = 2;
const float cellDim = 1. / gridSize;

const NSArray<NSString *> *labels = @[@"aeroplane", @"bicycle", @"bird", @"boat", @"bottle", @"bus", @"car", @"cat", @"chair", @"cow", @"diningtable", @"dog", @"horse", @"motorbike", @"person", @"pottedplant", @"sheep", @"sofa", @"train", @"tvmonitor"];

//http://www.mattrajca.com/2016/11/25/getting-started-with-deep-mnist-and-tensorflow-on-ios.html
@implementation YoloTinyV1Classifier

#pragma MARK - initialization

- (id) init {
    self = [super init];
    
    if(self) {
        boxes = [[NSMutableArray alloc] initWithCapacity:gridSize * gridSize * numBoxesPerCell];
    }
    
    return self;
}

#pragma MARK - implementation

- (void)loadModel {
    tf::Status status = tf::NewSession(tf::SessionOptions(), &self->session);
    
    if (!status.ok()) {
        LOG(ERROR) << "Error while creating session: " << status.ToString() << "\n";
        return;
    }
        
    NSString *modelPath = [[NSBundle mainBundle] pathForResource:@"frozen_yolo" ofType:@"pb"];
    tensorflow::GraphDef graph;
    
    status = tf::ReadBinaryProto(tf::Env::Default(), modelPath.fileSystemRepresentation, &graph);
        
    if (!status.ok()) {
        LOG(ERROR) << "Error while reading graph proto: " << status.ToString() << "\n";
        return;
    }
        
    status = self->session->Create(graph);
        
    if (!status.ok()) {
        LOG(ERROR) << "Error while creating session: " << status.ToString() << "\n";
        return;
    }
}
        
- (void)classifyImage:(CGImageRef) image {
    [boxes removeAllObjects];
    
    auto dataProvider = CGImageGetDataProvider(image);
    auto dataRef = CGDataProviderCopyData(dataProvider);
    UInt8* buffer = (UInt8*)CFDataGetBytePtr(dataRef);
    size_t bytesPerRow = CGImageGetBytesPerRow(image);
    
    tf::Tensor imageTensor(tensorflow::DT_FLOAT, tensorflow::TensorShape({1, imageDimension, imageDimension, 3}));
    auto mapped = imageTensor.tensor<float, 4>();
    auto outData = mapped.data();
    
    for(NSUInteger y = 0; y<imageDimension; y++) {
        for(NSUInteger x = 0; x<imageDimension; x++) {
            unsigned long inIndex = y * bytesPerRow + x * 4;
            
            float b = buffer[inIndex];
            float g = buffer[inIndex + 1];
            float r = buffer[inIndex + 2];
            
            NSUInteger outIndex = y * imageDimension * 3 + x * 3;
            
            outData[outIndex] = 2 * (r / 255.) - 1;
            outData[outIndex + 1] = 2 * (g / 255.) - 1;
            outData[outIndex + 2] = 2 * (b / 255.) - 1;
        }
    }
    CFRelease(dataRef);
    
    std::vector<tensorflow::Tensor> outputs;
    tensorflow::Status runStatus = self->session->Run({{inputTensorName, imageTensor}}, {outputTensorName}, {}, &outputs);
    
    if(!runStatus.ok()) {
        LOG(ERROR) << "Inference failed:" << runStatus;
    } else {
        tf::Tensor *output = &outputs[0];
        auto results = output->flat<float>();
        float *outValues = results.data();

        float *predictions = outValues;
        float *confidences = outValues + 980;
        float *coordinates = outValues + 1078;
        
        for(NSUInteger cellIndex = 0; cellIndex < gridSize * gridSize; cellIndex++) {
            for(NSUInteger boxIndex = 0; boxIndex < numBoxesPerCell; boxIndex++) {
                
                NSUInteger boxConfidenceIndex = cellIndex * numBoxesPerCell + boxIndex;
                assert(boxConfidenceIndex <= 98);
                
                float boxConfidence = confidences[boxConfidenceIndex];
                
                NSUInteger basePredictionsIndex = cellIndex * numClasses;
                assert(basePredictionsIndex <= 979);
                
                float *classPredictions = predictions + basePredictionsIndex;
                
                float highestClassProbability = -1.;
                NSUInteger highestClassProbabilityIndex = -1;
                
                // get class and index with highest probability
                for(NSUInteger classIndex = 0; classIndex < numClasses; classIndex++) {
                    if(classPredictions[classIndex] >= highestClassProbability) {
                        highestClassProbability = classPredictions[classIndex];
                        highestClassProbabilityIndex = classIndex;
                    }
                }
                
                float classConfidence = boxConfidence * highestClassProbability;
                
                if (classConfidence >= 0.1) {
                    NSUInteger gridRow = cellIndex / gridSize;
                    NSUInteger gridColumn = cellIndex % gridSize;
                    
                    NSUInteger baseCoordinatesIndex = (cellIndex * numBoxesPerCell * 4) + boxIndex * 4;
                    assert(baseCoordinatesIndex < 392);
                    
                    float x = (gridColumn * cellDim) + (coordinates[baseCoordinatesIndex] * cellDim);
                    float y = (gridRow * cellDim) + (coordinates[baseCoordinatesIndex + 1] * cellDim);
                    float width = powf(coordinates[baseCoordinatesIndex + 2], 1.8);
                    float height = powf(coordinates[baseCoordinatesIndex + 3], 1.8);
                    
                    [boxes addObject:
                        [[YoloV1Box alloc]
                            initWithLeft:x - (width / 2.) top:y - (height / 2.)
                            right:x + (width / 2.) bottom:y + (height / 2.)
                            confidence:classConfidence classIndex:highestClassProbabilityIndex
                         label:labels[highestClassProbabilityIndex]]];
                }
            }
        }
    }
}

- (NSArray<YoloV1Box *> *) result {
    return boxes;
}
        
- (void)close {
    if(self->session) {
        tf::Status status = self->session->Close();
        
        if (!status.ok()) {
            LOG(ERROR) << "Error while closing session: " << status.ToString() << "\n";
            return;
        }
        
        self->session = nil;
    }
}




@end
