import Foundation

@objc class YoloV1Box: NSObject {
    let left: Float;
    let top: Float;
    let right: Float;
    let bottom: Float;
    let confidence: Float;
    let classIndex: Int;
    let label: String;
    
    init(left: Float, top: Float, right: Float, bottom: Float, confidence: Float, classIndex: Int, label: String) {
        self.left = left;
        self.top = top;
        self.right = right;
        self.bottom = bottom;
        self.confidence = confidence;
        self.classIndex = classIndex;
        self.label = label;
    }
}
