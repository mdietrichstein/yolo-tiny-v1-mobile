import UIKit
import AVFoundation

class ViewController: UIViewController {

    // MARK: - Definitions
    let cropRectPortrait = CGRect(x: 0, y: 96, width: 448, height: 448)
    let cropRectLandscape = CGRect(x: 96, y: 0, width: 448, height: 448)

    let interfaceOrientationMap: [UIInterfaceOrientation : Int] = [
        .unknown           : 0,
        .portrait           : 0,
        .portraitUpsideDown : 180,
        .landscapeLeft      : 90,
        .landscapeRight     : 270,
        ]
    
    let videoOrientationMap: [AVCaptureVideoOrientation : Int] = [
        .portrait           : 0,
        .portraitUpsideDown : 180,
        .landscapeLeft      : 90,
        .landscapeRight     : 270,
    ]
    
    let classifier = YoloTinyV1Classifier()
    
    // MARK: - Views
    @IBOutlet var cameraPreviewView : UIView!
    @IBOutlet var croppedPreviewView : UIImageView!
    
    var overlayView: OverlayDrawer!
    var cameraPreviewLayer:AVCaptureVideoPreviewLayer?
    
    
    // MARK: - Camera Properties
    let session = AVCaptureSession()
    var captureDevice : AVCaptureDevice!
    var videoDataOutput: AVCaptureVideoDataOutput!
    var videoDataOutputQueue: DispatchQueue!
    
    // MARK: - Lifecycle
    override func viewDidLoad() {
        super.viewDidLoad()
        
        overlayView = OverlayDrawer(frame: view.bounds)
        view.addSubview(overlayView)
        
        cameraPreviewLayer = AVCaptureVideoPreviewLayer(session: session)
        cameraPreviewLayer?.videoGravity = AVLayerVideoGravityResizeAspect
        
        cameraPreviewView.layer.masksToBounds = true
        cameraPreviewView.layer.addSublayer(cameraPreviewLayer!)
    }
    
    override func viewWillAppear(_ animated: Bool) {
        classifier.loadModel()
    }
    
    override func viewDidAppear(_ animated: Bool) {
        authorizeCamera()
    }
    
    override func viewWillDisappear(_ animated: Bool) {
        stopCameraSession()
        classifier.close()
    }
    
    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        
        let newFrame = CGRect(x: 0, y: 0, width: cameraPreviewView.frame.size.width, height: cameraPreviewView.frame.size.height)
        overlayView?.frame = newFrame
        cameraPreviewLayer?.frame = newFrame
    }
    
    override func willRotate(to toInterfaceOrientation: UIInterfaceOrientation, duration: TimeInterval) {
        guard let cameraPreviewLayer = cameraPreviewLayer else {
            return
        }
        
        switch toInterfaceOrientation {
            case .portrait, .unknown:
                cameraPreviewLayer.connection.videoOrientation = .portrait
            case .portraitUpsideDown:
                cameraPreviewLayer.connection.videoOrientation = .portraitUpsideDown
            case .landscapeLeft:
                cameraPreviewLayer.connection.videoOrientation = .landscapeLeft
            case .landscapeRight:
                cameraPreviewLayer.connection.videoOrientation = .landscapeRight
        }
    }
    
    // MARK: - Permissions
    private func authorizeCamera() {
        AVCaptureDevice.requestAccess(forMediaType: AVMediaTypeVideo) { [weak self] (granted) in
            if(!granted) {
                DispatchQueue.main.async { [weak self] in
                    guard let strongSelf = self else { return }
                    strongSelf.showCameraAlert()
                }
            } else {
                guard let strongSelf = self else { return }
                strongSelf.startCaptureSession()
            }
        }
    }
    
    // MARK: - UI helpers
    private func showCameraAlert() {
        let alert = UIAlertController(title: "Camera Access Denied", message: "Please allow the app to access your camera", preferredStyle: .alert)
        
        alert.addAction(UIAlertAction.init(title: "Authorize", style: .default) { (a: UIAlertAction) in
            UIApplication.shared.open(URL(string: UIApplicationOpenSettingsURLString)!, options: [:], completionHandler: nil)
        })
        
        present(alert, animated: true, completion: nil)
    }
}

// MARK: - Camera Handling
extension ViewController:  AVCaptureVideoDataOutputSampleBufferDelegate {
    
    // MARK: - Camera Controls
    func startCaptureSession() {
        session.sessionPreset = AVCaptureSessionPreset640x480
        
        guard let device = AVCaptureDeviceDiscoverySession(
            deviceTypes: [AVCaptureDeviceType.builtInWideAngleCamera],
            mediaType: AVMediaTypeVideo, position: AVCaptureDevicePosition.back).devices.first
            else { return }

        captureDevice = device
        beginSession()
    }
    
    func stopCameraSession() {
        session.stopRunning()
    }
    
    // MARK: - Delegate Implementation
    func captureOutput(_ output: AVCaptureOutput!, didOutputSampleBuffer sampleBuffer: CMSampleBuffer!, from connection: AVCaptureConnection!) {
        let videoRotation = videoOrientationMap[connection.videoOrientation]!
        let interfaceRotation = interfaceOrientationMap[UIApplication.shared.statusBarOrientation]!
        
        let rotation = videoRotation - interfaceRotation
        let radians = (CGFloat(rotation) * .pi) / 180
        
        let imageBuffer = CMSampleBufferGetImageBuffer(sampleBuffer)
        
        let originalImage = CIImage(cvImageBuffer: imageBuffer!)
        
        let rotationTransform = CGAffineTransform(translationX: originalImage.extent.midX, y: originalImage.extent.midY)
            .rotated(by: radians).translatedBy(x: -originalImage.extent.midX, y: -originalImage.extent.midY)
        
        let image = CIImage(cvImageBuffer: imageBuffer!).applying(rotationTransform)
        
        // Cropping on CIImage produces weird results. CGImage seems to work better
        let ciCtx = CIContext.init(options: nil)
        let cgImage = ciCtx.createCGImage(image, from: image.extent)!
                            .cropping(to: ((rotation / 90) % 2) == 0 ? cropRectLandscape : cropRectPortrait)

        classifier.classifyImage(cgImage!)
        
        let boxes = classifier.result()
        
        DispatchQueue.main.async { [weak self] in
            guard let strongSelf = self else { return }
            strongSelf.croppedPreviewView.image = UIImage(cgImage: cgImage!)
            
            strongSelf.overlayView.boxes = boxes
            strongSelf.overlayView.setNeedsDisplay()
        }


    }
    
    // MARK: - Session Handling
    private func beginSession() {
        var deviceInput: AVCaptureDeviceInput?
        
        do {
            deviceInput = try AVCaptureDeviceInput(device: captureDevice)
        } catch let error as NSError {
            print("Unable to create camera device input: \(error.localizedDescription)")
            return
        }
        
        if(session.canAddInput(deviceInput)) {
            session.addInput(deviceInput!)
        }
        
        videoDataOutput = AVCaptureVideoDataOutput()
        videoDataOutput.videoSettings = [kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32BGRA]
        videoDataOutput.alwaysDiscardsLateVideoFrames = true
        videoDataOutputQueue = DispatchQueue(label: "VideoDataOutputQueue")
        videoDataOutput.setSampleBufferDelegate(self, queue: videoDataOutputQueue)
        
        if(session.canAddOutput(videoDataOutput)) {
            session.addOutput(videoDataOutput)
        }
        
        session.startRunning()
    }
}

