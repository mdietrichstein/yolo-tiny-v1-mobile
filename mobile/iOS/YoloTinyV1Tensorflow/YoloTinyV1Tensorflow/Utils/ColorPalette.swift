import Foundation

//https://sashat.me/2017/01/11/list-of-20-simple-distinct-colors/
class ColorPalette {
    
    private static let colors = [
        color(red: 60, green: 180, blue: 75),
        color(red: 230, green: 25, blue: 75),
        color(red: 255, green: 225, blue: 25),
        color(red: 0, green: 130, blue: 200),
        color(red: 245, green: 130, blue: 48),
        color(red: 145, green: 30, blue: 180),
        color(red: 70, green: 240, blue: 240),
        color(red: 240, green: 50, blue: 230),
        color(red: 210, green: 245, blue: 60),
        color(red: 250, green: 190, blue: 190),
        color(red: 0, green: 128, blue: 128),
        color(red: 230, green: 190, blue: 255),
        color(red: 170, green: 110, blue: 40),
        color(red: 255, green: 250, blue: 200),
        color(red: 128, green: 0, blue: 0),
        color(red: 170, green: 255, blue: 195),
        color(red: 128, green: 128, blue: 0),
        color(red: 255, green: 215, blue: 180),
        color(red: 0, green: 0, blue: 128),
        color(red: 128, green: 128, blue: 128),
        color(red: 255, green: 255, blue: 255),
        color(red: 0, green: 0, blue: 0)
    ]
    
    static func colorForIndex(i: Int) -> UIColor {
        return colors[i % colors.count]
    }
    
    private static func color(red: CGFloat, green: CGFloat, blue: CGFloat) -> UIColor {
        return UIColor(red: red / 255, green: green / 255, blue: blue / 255, alpha: 1)
    }
}
