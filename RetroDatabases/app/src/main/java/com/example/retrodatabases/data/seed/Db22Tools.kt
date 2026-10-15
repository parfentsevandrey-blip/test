package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 1820 to 1830: dictionaries, a surveying mirror, a galvanometer and calculation and astronomy tables. */
val db22Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 22,
        kind = Kind.TOOL,
        section = "Dictionaries",
        title = "Webster's American Dictionary of the English Language",
        originalTitle = "An American Dictionary of the English Language (1828)",
        author = "Noah Webster",
        year = 1828,
        summary = "Two volumes that fixed American spellings such as color, center and honor, and gave definitions aimed at readers in the United States.",
        details = "Webster published the dictionary in New York in 1828 in two volumes. Its spellings spread through American printing in the following decades, and they still separate American usage from British.",
    ),

    entry(
        databaseId = 22,
        kind = Kind.TOOL,
        section = "Chemistry",
        title = "Ure's Dictionary of Chemistry",
        originalTitle = "A Dictionary of Chemistry (1821)",
        author = "Andrew Ure",
        year = 1821,
        summary = "A reference that gave chemical substances, terms and processes in alphabetical order for manufacturers and students.",
        details = "Ure published the dictionary in London in 1821, and its entries describe processes used in trade as well as laboratory chemistry. It was a practical handbook as much as a glossary.",
    ),

    entry(
        databaseId = 22,
        kind = Kind.TOOL,
        section = "Surveying",
        title = "Gauss's heliotrope for survey sightings",
        originalTitle = "Heliotrop (1821)",
        author = "Carl Friedrich Gauss",
        year = 1821,
        summary = "A mirror on a stand that flashed the sun's light toward a distant survey station, so the station could be sighted across many kilometres.",
        details = "Gauss developed the heliotrope in 1821 to make sightings possible across the long lines of the Hanover triangulation. It was used in that survey through 1825.",
    ),

    entry(
        databaseId = 22,
        kind = Kind.TOOL,
        section = "Galvanometers",
        title = "Nobili's astatic galvanometer",
        originalTitle = "Astatic pair of needles (1825)",
        author = "Leopoldo Nobili",
        year = 1825,
        summary = "Two magnetic needles hung one above the other with their poles reversed, so the Earth's field pulls on the pair with almost no net force and a small current swings it far.",
        details = "Nobili described the astatic pair in 1825, and it made galvanometers much more sensitive than a single needle. The name comes from the Greek for not standing still, since the needle pair has no tendency to settle toward north.",
        rare = true,
    ),

    entry(
        databaseId = 22,
        kind = Kind.TOOL,
        section = "Astronomical tables",
        title = "Bessel's Tabulae Regiomontanae",
        originalTitle = "Tabulae Regiomontanae reductionum (1830)",
        author = "Friedrich Wilhelm Bessel",
        year = 1830,
        summary = "Bessel's tables for reducing astronomical observations, so that a measured reading can be corrected for known effects and compared with other readings.",
        details = "Bessel published the tables in Konigsberg in 1830 through the Borntraeger brothers. Astronomers used such tables to turn raw readings into corrected positions, a routine job that the tables made quicker and less error-prone.",
    ),

    entry(
        databaseId = 22,
        kind = Kind.TOOL,
        section = "Calculation tables",
        title = "Crelle's Rechentafeln, the multiplication tables",
        originalTitle = "Rechentafeln, aids to multiplication and division (1820)",
        author = "August Leopold Crelle",
        year = 1820,
        summary = "Two small volumes of tables that let a calculator multiply and divide by looking up products and quotients, sparing long hand working.",
        details = "Crelle's tables appeared in Berlin in 1820 as two small volumes, and later printings followed in German, English and French. They were made for everyday calculation, when a table of products was faster than long multiplication by hand.",
        rare = true,
    ),
)
