package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Built products of 2000 to 2010: finished programs with an explanation of how they work and instructions for running them. */
val db3Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 3,
        kind = Kind.PROJECT,
        section = "Audio and feeds",
        title = "Podcast feed reader in Python",
        originalTitle = "Episode lister for RSS enclosures",
        author = "Written in the style of the 2005 podcast clients",
        year = 2005,
        summary = "A small tool that reads a podcast feed and prints each episode with the address of its audio file.",
        details = "Podcasts became popular in 2004 and 2005. Each episode was an RSS item with an enclosure element that pointed to an audio file. " +
            "The tool parses the feed, skips items without an enclosure, and prints the title and the audio address. A download script could use the same addresses.",
        instructions = """
            1. Save the code as podcasts.py.
            2. Save a feed as feed.xml, with one item that has an enclosure element.
            3. Run it: python3 podcasts.py feed.xml
        """,
        example = """
            #!/usr/bin/env python3
            # Lists the episodes of a podcast feed (RSS 2.0 with enclosures) and their audio addresses.
            import sys
            import xml.etree.ElementTree as ET

            root = ET.parse(sys.argv[1]).getroot()
            for item in root.findall("./channel/item"):
                enclosure = item.find("enclosure")
                if enclosure is not None:
                    sys.stdout.write("%s: %s\n" % (item.findtext("title"), enclosure.get("url")))
        """,
    ),

    entry(
        databaseId = 3,
        kind = Kind.PROJECT,
        section = "Writing tools",
        title = "A personal wiki with CamelCase links",
        originalTitle = "Personal WikiWords renderer",
        author = "Written in the style of the 2002 personal wikis",
        year = 2002,
        summary = "A one-file renderer that turns CamelCase words in a text page into links to other pages.",
        details = "Wiki software of the early 2000s let anyone link pages by writing CamelCase words. " +
            "This renderer finds every WikiWord with a regular expression and turns it into a link to a page of the same name. " +
            "Paragraphs are separated by blank lines, and the output is plain HTML that any browser shows.",
        instructions = """
            1. Save the code as wiki.py.
            2. Write a page in HomePage.txt, with some WikiWords in it.
            3. Run it: python3 wiki.py HomePage.txt > HomePage.html, then open HomePage.html in a browser.
        """,
        example = """
            #!/usr/bin/env python3
            # A personal wiki in one file: CamelCase words become links to other pages.
            import re
            import sys

            LINK = re.compile(r"\b([A-Z][a-z]+(?:[A-Z][a-z]+)+)\b")

            def render(text):
                html = LINK.sub(r'<a href="\1.html">\1</a>', text)
                paragraphs = html.split("\n\n")
                return "\n".join("<p>%s</p>" % p.strip() for p in paragraphs if p.strip())

            if __name__ == "__main__":
                print(render(open(sys.argv[1]).read()))
        """,
        rare = true,
    ),

    entry(
        databaseId = 3,
        kind = Kind.PROJECT,
        section = "Web services",
        title = "Google Maps mash-up in JavaScript",
        originalTitle = "Map mash-up (2005)",
        author = "Written in the style of the 2005 mash-up craze",
        year = 2005,
        summary = "A web page that places markers on a Google map from a list of places, the classic mash-up of the decade.",
        details = "The Google Maps API (2005) let any web page embed a map and draw on it with a few script calls. " +
            "A mash-up combined a public data source with a map. This page shows the pattern: a map, a centre point and one marker for each place.",
        instructions = """
            1. Get an API key for the map service.
            2. Put the script in an HTML page that loads the map script.
            3. Open the page in a browser that is allowed to load the map.
        """,
        example = """
            var map = new GMap2(document.getElementById("map"));
            map.setCenter(new GLatLng(51.5, -0.12), 12);

            var places = [
              { name: "Museum", lat: 51.5194, lng: -0.1270 },
              { name: "Park", lat: 51.5025, lng: -0.1419 }
            ];
            for (var i = 0; i < places.length; i++) {
              var marker = new GMarker(new GLatLng(places[i].lat, places[i].lng));
              map.addOverlay(marker);
            }
        """,
    ),

    entry(
        databaseId = 3,
        kind = Kind.PROJECT,
        section = "Browsers",
        title = "A Firefox extension in XUL",
        originalTitle = "Firefox overlay extension",
        author = "Written in the style of the first Firefox extensions",
        year = 2004,
        summary = "A small browser extension that adds a menu item through a XUL overlay and a script file.",
        details = "Firefox 1.0 (2004) let developers add features with extensions. An extension defined XUL overlays that merged new elements " +
            "into the browser window, and a script file that ran the feature. The package was installed from a file with an install manifest.",
        instructions = """
            1. Put the overlay and the script in one folder, with an install.rdf manifest.
            2. Pack the folder into an .xpi file.
            3. Open the file in Firefox to install the extension.
        """,
        example = """
            <?xml version="1.0"?>
            <overlay id="clock-overlay"
                     xmlns="http://www.mozilla.org/keymaster/gatekeeper/there.is.only.xul">
              <script src="clock.js"/>
              <menupopup id="viewMenuPopup">
                <menuitem label="Show the clock" oncommand="showClock();"/>
              </menupopup>
            </overlay>
        """,
        rare = true,
    ),
)
