import UIKit
import CoreText

class OverlayDrawer: UIView {
    
    // MARK: - State
    var boxes: [YoloV1Box]?
    
    // MARK: Configuration
    let textFont = UIFont(name: "HelveticaNeue-Thin", size: 18)
    let paragraphStyle = NSMutableParagraphStyle()
    
    // MARK: - Initialization
    override init(frame: CGRect) {
        super.init(frame: frame)
        paragraphStyle.alignment = .left
        backgroundColor = UIColor.clear
    }
    
    required init?(coder aDecoder: NSCoder) {
        super.init(coder: aDecoder)
    }
    
    // MARK: - Interface
    
    override func draw(_ rect: CGRect) {
        guard let boxes = self.boxes,
              let ctx = UIGraphicsGetCurrentContext()
              else { return }
        
        ctx.setLineWidth(2)
        ctx.setTextDrawingMode(.fill)
        
        boxes.forEach { box in
            let color = ColorPalette.colorForIndex(i: box.classIndex)
            ctx.setStrokeColor(color.cgColor)
            
            let width = box.right - box.left
            let height = box.bottom - box.top
            
            var rectangle: CGRect?
            
            if bounds.width > bounds.height {
                // Landscape
                let squareSize = Float(bounds.height)
                let xOffset = (bounds.width - bounds.height) / 2
                
                rectangle = CGRect(
                    x: CGFloat(box.left * squareSize) + xOffset,
                    y: CGFloat(box.top * squareSize),
                    width: CGFloat(width * squareSize),
                    height: CGFloat(height * squareSize))
            } else {
                // Portrait
                let squareSize = Float(bounds.width)
                let yOffset = (bounds.height - bounds.width) / 2
                
                rectangle = CGRect(
                    x: CGFloat(box.left * squareSize),
                    y: CGFloat(box.top * squareSize) + yOffset,
                    width: CGFloat(width * squareSize),
                    height: CGFloat(height * squareSize))
            }
            
            ctx.stroke(rectangle!)
            
            let textAttributes = [
                NSForegroundColorAttributeName: color,
                NSFontAttributeName: textFont,
                NSParagraphStyleAttributeName: paragraphStyle]

            String(format:"\(box.label) (%.2f)", box.confidence).draw(with: rectangle!, options: .usesLineFragmentOrigin, attributes: textAttributes, context: nil)
        }
    }
    
    func drawBoxes(boxes: [YoloV1Box]!) {
        boxes.forEach { box in
            print(box)
        }
    }
}
