import AVFoundation
import AppKit
import SwiftUI

/// Слой AVPlayerLayer в SwiftUI. Масштаб, поворот, отражение и скругление
/// применяются прямо к CALayer — без перерисовки SwiftUI и с аппаратным композитингом.
struct VideoSurface: NSViewRepresentable {
    let engine: PlayerEngine
    var cornerRadius: CGFloat
    var gravity: VideoGravity
    var zoom: CGFloat
    var rotation: Int
    var mirrored: Bool

    func makeNSView(context: Context) -> PlayerLayerView {
        let view = PlayerLayerView()
        view.playerLayer.player = engine.player
        engine.attachPictureInPicture(to: view.playerLayer)
        return view
    }

    func updateNSView(_ view: PlayerLayerView, context: Context) {
        view.configure(cornerRadius: cornerRadius, gravity: gravity.avGravity, zoom: zoom, rotation: rotation, mirrored: mirrored)
    }
}

final class PlayerLayerView: NSView {
    let playerLayer = AVPlayerLayer()
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
        playerLayer.videoGravity = .resizeAspect
        playerLayer.backgroundColor = NSColor.black.cgColor
        root.addSublayer(playerLayer)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError() }

    override var isFlipped: Bool { true }
    override var mouseDownCanMoveWindow: Bool { false }

    func configure(cornerRadius: CGFloat, gravity: AVLayerVideoGravity, zoom: CGFloat, rotation: Int, mirrored: Bool) {
        CATransaction.begin()
        CATransaction.setAnimationDuration(0.35)
        CATransaction.setAnimationTimingFunction(CAMediaTimingFunction(name: .easeInEaseOut))
        layer?.cornerRadius = cornerRadius
        if playerLayer.videoGravity != gravity { playerLayer.videoGravity = gravity }
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
