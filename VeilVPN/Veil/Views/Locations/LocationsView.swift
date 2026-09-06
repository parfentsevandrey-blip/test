import SwiftUI

struct LocationsView: View {
    @Environment(AppState.self) private var app

    private let columns = [GridItem(.adaptive(minimum: 172, maximum: 240), spacing: 14)]

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                VStack(alignment: .leading, spacing: 4) {
                    Text("Exit relay country")
                        .font(.title2.weight(.bold))
                    Text("Pins the last relay of the circuit to one country. Automatic selection is the most private choice; changes apply immediately.")
                        .font(.callout)
                        .foregroundStyle(.secondary)
                        .fixedSize(horizontal: false, vertical: true)
                }
                LazyVGrid(columns: columns, spacing: 14) {
                    LocationCard(
                        flag: "🌐",
                        title: Text("Automatic"),
                        detail: Text("Recommended"),
                        isSelected: app.settings.exitCountry == nil
                    ) {
                        app.setExitCountry(nil)
                    }
                    ForEach(ExitLocation.popular) { location in
                        LocationCard(
                            flag: location.flag,
                            title: Text(verbatim: location.name),
                            detail: Text(verbatim: location.code.uppercased()),
                            isSelected: app.settings.exitCountry == location.code
                        ) {
                            app.setExitCountry(location.code)
                        }
                    }
                }
            }
            .padding(32)
        }
    }
}

struct LocationCard: View {
    let flag: String
    let title: Text
    let detail: Text
    let isSelected: Bool
    let action: @MainActor () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 12) {
                Text(flag)
                    .font(.system(size: 30))
                VStack(alignment: .leading, spacing: 2) {
                    title
                        .font(.subheadline.weight(.semibold))
                        .lineLimit(1)
                    detail
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                Spacer(minLength: 0)
                Image(systemName: isSelected ? "checkmark.circle.fill" : "circle")
                    .foregroundStyle(isSelected ? AnyShapeStyle(.tint) : AnyShapeStyle(.tertiary))
                    .imageScale(.large)
            }
            .padding(14)
            .contentShape(.rect(cornerRadius: 18))
        }
        .buttonStyle(.plain)
        .glassEffect(.regular.tint(isSelected ? Color.accentColor.opacity(0.35) : nil).interactive(), in: .rect(cornerRadius: 18))
        .animation(.smooth(duration: 0.25), value: isSelected)
    }
}
