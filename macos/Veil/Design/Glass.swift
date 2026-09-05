import SwiftUI

// The Tahoe design language, in one file.
//
// Every glass surface in this app goes through these types rather than
// calling `.glassEffect` directly, for a reason that is not tidiness. Liquid
// Glass is new, its API surface is still moving, and an app that scatters the
// modifier across forty views has forty places to fix when it moves. Here
// there is one, and the rest of the app asks for a *kind of surface* —
// a panel, a control, a status field — rather than for a material.
//
// The rules the layout follows, which are Apple's rather than ours:
//
//   - Glass is for the layer above the content, never for the content. Panels
//     and controls float; text and data sit on the ground.
//   - Glass near glass should merge rather than stack. That is what
//     GlassEffectContainer does, and why panels that belong together are
//     grouped in one instead of being given the material individually.
//   - Corners are concentric: a control inside a panel takes the panel's
//     radius minus the inset, so the curves stay parallel instead of
//     drifting apart. `Radius.inner(_:inset:)` is that arithmetic.
//   - Colour is carried by the content. A tint on the glass itself is for one
//     thing at a time — here, the connect control, and nothing else.

// MARK: - Tokens

enum Metrics {
    /// The 4-point grid everything lands on.
    static let hairline: CGFloat = 1
    static let tight: CGFloat = 6
    static let snug: CGFloat = 10
    static let regular: CGFloat = 16
    static let loose: CGFloat = 24
    static let wide: CGFloat = 32

    /// Inset from a panel's edge to its content.
    static let panelPadding: CGFloat = 18
}

enum Radius {
    static let panel: CGFloat = 20
    static let control: CGFloat = 12
    static let field: CGFloat = 10

    /// The radius a shape should take to stay concentric inside another.
    ///
    /// Two rounded rectangles look wrong together when their radii are equal
    /// and their edges are not: the inner curve is visibly tighter than the
    /// gap around it. Subtracting the inset keeps the curves parallel, which
    /// is the whole of why nested Tahoe surfaces look settled.
    static func inner(_ outer: CGFloat, inset: CGFloat) -> CGFloat {
        max(4, outer - inset)
    }
}

/// What the tunnel's state means in colour, in one place.
///
/// Deliberately not a rainbow. Green is reserved for a tunnel that has been
/// *proved* to carry traffic, because that is the one claim this app is
/// careful about; everything on the way there is the accent, and only a real
/// failure is red.
enum StatusTone {
    case idle, working, live, failed

    var color: Color {
        switch self {
        case .idle: return .secondary
        case .working: return .accentColor
        case .live: return .green
        case .failed: return .red
        }
    }
}

// MARK: - Surfaces

/// A floating panel: the app's main unit of layout.
struct GlassPanel<Content: View>: View {
    var radius: CGFloat = Radius.panel
    var padding: CGFloat = Metrics.panelPadding
    @ViewBuilder var content: Content

    var body: some View {
        content
            .padding(padding)
            .frame(maxWidth: .infinity, alignment: .leading)
            .veilGlass(shape: .rect(cornerRadius: radius))
    }
}

/// Groups surfaces that belong together so the material merges between them
/// instead of stacking into a brighter patch where they overlap.
struct GlassGroup<Content: View>: View {
    var spacing: CGFloat = Metrics.regular
    @ViewBuilder var content: Content

    var body: some View {
        if #available(macOS 26.0, *) {
            GlassEffectContainer(spacing: spacing) { content }
        } else {
            content
        }
    }
}

extension View {
    /// The one place the material is applied.
    ///
    /// Falls back to a plain material below Tahoe so the app still builds and
    /// runs while the design language is not available — the layout is the
    /// same, only the surface is flatter.
    @ViewBuilder
    func veilGlass(shape: some Shape, tint: Color? = nil, interactive: Bool = false) -> some View {
        if #available(macOS 26.0, *) {
            // Built in a plain function rather than here: inside a
            // @ViewBuilder every statement is read as a view, so an
            // assignment evaluates to () and () is not a View.
            self.glassEffect(Glass.veil(tint: tint, interactive: interactive), in: shape)
        } else {
            self.background(.regularMaterial, in: shape)
                .overlay(shape.stroke(.white.opacity(0.08), lineWidth: Metrics.hairline))
        }
    }

    /// For the one control that is allowed to carry colour in its material.
    @ViewBuilder
    func veilProminentGlass(shape: some Shape, tint: Color) -> some View {
        if #available(macOS 26.0, *) {
            self.glassEffect(Glass.regular.tint(tint).interactive(), in: shape)
        } else {
            self.background(tint.opacity(0.22), in: shape)
                .overlay(shape.stroke(tint.opacity(0.5), lineWidth: Metrics.hairline))
        }
    }
}

// MARK: - Type

extension Font {
    /// The number on the tunnel screen: large, tabular, and never re-flowing
    /// as digits change.
    static let veilReadout = Font.system(size: 34, weight: .semibold, design: .rounded)
        .monospacedDigit()
    static let veilTitle = Font.system(size: 17, weight: .semibold)
    static let veilLabel = Font.system(size: 11, weight: .medium)
    static let veilMono = Font.system(size: 11, design: .monospaced)
}

// MARK: - Small components

/// A measured value with its name under it. The unit of the tunnel screen.
struct Readout: View {
    let label: String
    let value: String
    var systemImage: String? = nil
    var tone: Color = .primary

    var body: some View {
        VStack(alignment: .leading, spacing: Metrics.tight) {
            HStack(spacing: 5) {
                if let systemImage {
                    Image(systemName: systemImage)
                        .font(.system(size: 10, weight: .semibold))
                }
                Text(label.uppercased())
                    .font(.veilLabel)
                    .kerning(0.6)
            }
            .foregroundStyle(.secondary)

            Text(value)
                .font(.system(size: 19, weight: .semibold, design: .rounded))
                .monospacedDigit()
                .foregroundStyle(tone)
                .lineLimit(1)
                .minimumScaleFactor(0.7)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/// A small round-rect badge, for a route name or a count.
struct Chip: View {
    let text: String
    var tone: Color = .secondary

    var body: some View {
        Text(text)
            .font(.veilLabel)
            .foregroundStyle(tone)
            .padding(.horizontal, 8)
            .padding(.vertical, 4)
            .veilGlass(shape: .capsule)
    }
}

/// The dot that says what the tunnel is doing, with a pulse while it works.
struct StatusDot: View {
    let tone: StatusTone
    var animating: Bool = false
    @State private var wide = false

    var body: some View {
        Circle()
            .fill(tone.color)
            .frame(width: 9, height: 9)
            .overlay(
                Circle()
                    .stroke(tone.color.opacity(wide ? 0 : 0.55), lineWidth: 4)
                    .scaleEffect(wide ? 2.4 : 1)
            )
            .animation(
                animating
                    ? .easeOut(duration: 1.4).repeatForever(autoreverses: false)
                    : .default,
                value: wide
            )
            .onChange(of: animating, initial: true) { _, on in
                wide = on
            }
    }
}

@available(macOS 26.0, *)
extension Glass {
    /// The app's one material, with its two optional adjustments.
    ///
    /// A `tint` is reserved for the connect control, and `interactive` for
    /// surfaces that respond to a pointer; everything else takes the plain
    /// regular glass.
    static func veil(tint: Color?, interactive: Bool) -> Glass {
        var glass = Glass.regular
        if let tint { glass = glass.tint(tint) }
        if interactive { glass = glass.interactive() }
        return glass
    }
}
