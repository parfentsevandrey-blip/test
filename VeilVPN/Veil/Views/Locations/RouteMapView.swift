import MapKit
import SwiftUI

/// The circuit on a real map: one marker per hop with a known country, joined by a line.
struct RouteMapView: View {
    let hops: [CircuitHop]

    @State private var position: MapCameraPosition = .automatic

    private struct Point: Identifiable {
        let id: String
        let title: String
        let flag: String
        let coordinate: CLLocationCoordinate2D
        let isExit: Bool
    }

    private var points: [Point] {
        hops.compactMap { hop in
            guard let code = hop.countryCode, let coordinate = CountryCoordinates.centroid(for: code) else { return nil }
            return Point(id: hop.id, title: hop.countryName ?? hop.nickname, flag: hop.flag, coordinate: coordinate, isExit: hop.role == .exit)
        }
    }

    var body: some View {
        let points = self.points
        Map(position: $position) {
            if points.count > 1 {
                MapPolyline(coordinates: points.map(\.coordinate))
                    .stroke(.mint, lineWidth: 3)
            }
            ForEach(points) { point in
                Annotation(point.title, coordinate: point.coordinate) {
                    ZStack {
                        Circle()
                            .fill(point.isExit ? Color.mint : Color.orange)
                            .frame(width: 14, height: 14)
                            .shadow(color: (point.isExit ? Color.mint : Color.orange).opacity(0.8), radius: 8)
                        Text(point.flag)
                            .font(.system(size: 18))
                            .offset(y: -20)
                    }
                }
            }
        }
        .mapStyle(.standard(elevation: .flat, emphasis: .muted, pointsOfInterest: .excludingAll))
        .mapControlVisibility(.hidden)
        .overlay(alignment: .bottomLeading) {
            if points.isEmpty {
                Text("Relay locations appear here once the circuit is built.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .padding(8)
                    .glassEffect(.regular, in: .capsule)
                    .padding(10)
            }
        }
        .onChange(of: hops) { _, _ in
            position = .automatic
        }
        .accessibilityLabel(Text("Map of the Tor circuit"))
    }
}

/// Approximate country centroids for the map (lowercase ISO 3166-1 alpha-2).
enum CountryCoordinates {
    static func centroid(for code: String) -> CLLocationCoordinate2D? {
        guard let pair = table[code.lowercased()] else { return nil }
        return CLLocationCoordinate2D(latitude: pair.0, longitude: pair.1)
    }

    private static let table: [String: (Double, Double)] = [
        "us": (39.8, -98.6), "de": (51.2, 10.4), "nl": (52.1, 5.3), "fr": (46.6, 2.2), "gb": (54.0, -2.5),
        "se": (62.0, 15.0), "ch": (46.8, 8.2), "fi": (64.0, 26.0), "at": (47.6, 14.1), "ca": (56.1, -106.3),
        "no": (64.6, 11.0), "dk": (56.0, 10.0), "pl": (52.0, 19.1), "cz": (49.8, 15.5), "ro": (45.9, 24.9),
        "es": (40.4, -3.7), "it": (42.5, 12.5), "jp": (36.2, 138.2), "sg": (1.35, 103.8), "au": (-25.3, 133.8),
        "ua": (48.4, 31.2), "lu": (49.8, 6.1), "is": (64.9, -19.0), "ee": (58.6, 25.0), "lv": (56.9, 24.6),
        "md": (47.4, 28.4), "bg": (42.7, 25.5), "hk": (22.3, 114.2), "kr": (36.5, 127.9), "br": (-14.2, -51.9),
        "ru": (61.5, 105.3), "tr": (39.0, 35.2), "in": (20.6, 78.9), "cn": (35.9, 104.2), "ir": (32.4, 53.7),
        "kz": (48.0, 68.0), "by": (53.7, 27.9), "rs": (44.0, 21.0), "hu": (47.2, 19.5), "sk": (48.7, 19.7),
        "lt": (55.2, 23.9), "pt": (39.4, -8.2), "ie": (53.1, -8.2), "be": (50.5, 4.5), "gr": (39.1, 21.8),
        "il": (31.0, 34.9), "ae": (23.4, 53.8), "za": (-30.6, 22.9), "mx": (23.6, -102.5), "ar": (-38.4, -63.6),
        "cl": (-35.7, -71.5), "ge": (42.3, 43.4), "am": (40.1, 45.0), "az": (40.1, 47.6), "uz": (41.4, 64.6),
        "th": (15.9, 101.0), "vn": (14.1, 108.3), "id": (-0.8, 113.9), "my": (4.2, 102.0), "ph": (12.9, 121.8),
        "tw": (23.7, 121.0), "sa": (23.9, 45.1), "eg": (26.8, 30.8), "ng": (9.1, 8.7), "ke": (-0.02, 37.9),
        "ma": (31.8, -7.1), "co": (4.6, -74.3), "pe": (-9.2, -75.0), "nz": (-40.9, 174.9), "hr": (45.1, 15.2),
        "si": (46.2, 15.0), "mk": (41.6, 21.7), "al": (41.2, 20.2), "ba": (43.9, 17.7), "me": (42.7, 19.4),
        "cy": (35.1, 33.4), "mt": (35.9, 14.4), "li": (47.2, 9.6), "pa": (8.5, -80.8), "cr": (9.7, -83.8),
        "do": (18.7, -70.2), "pr": (18.2, -66.6), "lk": (7.9, 80.8), "np": (28.4, 84.1), "bd": (23.7, 90.4),
        "pk": (30.4, 69.3), "mn": (46.9, 103.8), "kg": (41.2, 74.8), "tj": (38.9, 71.3), "kh": (12.6, 105.0),
        "qa": (25.4, 51.2), "kw": (29.3, 47.5), "bh": (26.1, 50.6), "om": (21.5, 55.9), "jo": (30.6, 36.2),
        "lb": (33.9, 35.9), "iq": (33.2, 43.7), "tn": (33.9, 9.5), "dz": (28.0, 1.7), "gh": (7.9, -1.0),
        "sn": (14.5, -14.5), "ug": (1.4, 32.3), "tz": (-6.4, 34.9), "mu": (-20.3, 57.6), "sc": (-4.7, 55.5),
    ]
}
