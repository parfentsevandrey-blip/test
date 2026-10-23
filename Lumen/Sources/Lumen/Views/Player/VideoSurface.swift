import AVFoundation
import AppKit
import SwiftUI

/// Слой с картинкой движка (AVPlayerLayer или AVSampleBufferDisplayLayer) в SwiftUI. Масштаб, поворот,
/// отражение и скругление применяются прямо к CALayer — без перерисовки SwiftUI и с аппаратным композитингом.
struct VideoSurface: NSViewRepresentable {
    let videoLayer: CALayer?
    var cornerRadius: CGFloat
    var zoom: CGFloat
    var rotation: Int
    var mirrored: Bool

    func makeNSView(context: Context) -> PlayerLayerView {
        PlayerLayerView()
    }

    func updateNSView(_ view: PlayerLayerView, context: Context) {
        view.host(videoLayer)
        view.configure(cornerRadius: cornerRadius, zoom: zoom, rotation: rotation, mirrored: mirrored)
    }
}

final class PlayerLayerView: NSView {
    private var playerLayer: CALayer = CALayer()
    private var zoom: CGFloat = 1
    private var rotation = 0
    private var mirrored = false

    override init(frame: NSRect) {
        super.init(frame: frame)
        wantsLayer = true
        let root = CALayer()
        root.backgroundColor = NSColor.black.cgColor
        root.masksToBounds = true
        root.cornerCurve = .continuous
        layer = root
        root.addSublayer(playerLayer)
    }

    func host(_ video: CALayer?) {
        let next = video ?? CALayer()
        guard next !== playerLayer else { return }
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        playerLayer.removeFromSuperlayer()
        playerLayer = next
        layer?.addSublayer(next)
        applyGeometry()
        CATransaction.commit()
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError() }

    override var isFlipped: Bool { true }
    override var mouseDownCanMoveWindow: Bool { false }

    func configure(cornerRadius: CGFloat, zoom: CGFloat, rotation: Int, mirrored: Bool) {
        guard layer?.cornerRadius != cornerRadius || self.zoom != zoom || self.rotation != rotation || self.mirrored != mirrored else { return }
        CATransaction.begin()
        CATransaction.setAnimationDuration(0.35)
        CATransaction.setAnimationTimingFunction(CAMediaTimingFunction(name: .easeInEaseOut))
        layer?.cornerRadius = cornerRadius
        self.zoom = zoom
        self.rotation = rotation
        self.mirrored = mirrored
        applyGeometry()
        CATransaction.commit()
    }

    override func layout() {
        super.layout()
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        applyGeometry()
        CATransaction.commit()
    }

    private func applyGeometry() {
        let size = bounds.size
        let quarter = rotation % 180 != 0
        playerLayer.bounds = CGRect(origin: .zero, size: quarter ? CGSize(width: size.height, height: size.width) : size)
        playerLayer.position = CGPoint(x: size.width / 2, y: size.height / 2)
        var t = CATransform3DIdentity
        t = CATransform3DRotate(t, CGFloat(rotation) * .pi / 180, 0, 0, 1)
        t = CATransform3DScale(t, zoom * (mirrored ? -1 : 1), zoom, 1)
        playerLayer.transform = t
    }
}
