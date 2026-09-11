import Foundation

/// The affinity key for a destination: the registrable domain, so `a.example.com` and
/// `b.example.com` share a circuit while `example.com` and `example.org` do not.
///
/// This is an affinity key, not a security boundary. No Public Suffix List is bundled; a wrong
/// grouping costs at most a shared circuit between two sibling domains, which is what happens to
/// everything today.
enum HostKey {
    static func site(_ host: String) -> String {
        var value = host.lowercased()
        if value.hasSuffix(".") { value.removeLast() }
        if value.isEmpty { return host }
        if value.hasPrefix("[") { return value }                       // IPv6 literal
        if SOCKS5.ipv4Octets(value) != nil { return value }            // IPv4 literal
        if value.hasSuffix(".onion") { return value }                  // the whole address matters
        let labels = value.split(separator: ".", omittingEmptySubsequences: false).map(String.init)
        guard labels.count > 2 else { return value }
        let lastTwo = labels.suffix(2).joined(separator: ".")
        if multiLabelSuffixes.contains(lastTwo), labels.count >= 3 {
            return labels.suffix(3).joined(separator: ".")
        }
        return lastTwo
    }

    /// Two groups: country second-levels, and hosting suffixes where merging siblings would be a
    /// real grouping error (every `*.pages.dev` site would otherwise share one circuit).
    static let multiLabelSuffixes: Set<String> = [
        "co.uk", "org.uk", "ac.uk", "gov.uk", "com.au", "net.au", "org.au", "co.nz", "co.jp",
        "co.kr", "com.cn", "com.tw", "com.hk", "com.sg", "co.in", "com.br", "com.ar", "com.mx",
        "com.tr", "co.za", "co.il", "com.ua", "com.pl", "com.my", "co.th",
        "github.io", "gitlab.io", "pages.dev", "workers.dev", "r2.dev", "netlify.app",
        "vercel.app", "herokuapp.com", "azurewebsites.net", "azurestaticapps.net",
        "cloudfront.net", "appspot.com", "cloudfunctions.net", "run.app", "firebaseapp.com",
        "web.app", "fly.dev", "onrender.com", "railway.app", "ondigitalocean.app", "supabase.co",
        "deno.dev", "glitch.me", "repl.co", "blogspot.com", "wordpress.com", "myshopify.com",
        "wixsite.com", "squarespace.com", "sharepoint.com", "notion.site", "webflow.io",
        "readthedocs.io", "itch.io", "surge.sh",
    ]
}
