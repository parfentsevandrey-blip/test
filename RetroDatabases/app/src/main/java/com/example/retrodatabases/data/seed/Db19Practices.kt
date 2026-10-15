package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 1850 to 1860: shared standards for ocean logs, electrical units, measures and a reading code. */
val db19Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 19,
        kind = Kind.PRACTICE,
        section = "Ocean observation",
        title = "Brussels maritime conference of 1853",
        originalTitle = "International Maritime Conference, Brussels (1853)",
        author = "Matthew Fontaine Maury and Adolphe Quetelet",
        year = 1853,
        summary = "Ten nations agreed a shared format for logging weather and sea readings aboard ships, so that the records could be compared across navies and merchant fleets.",
        details = "The conference met in August and September 1853, on Maury's proposal and with Quetelet's help. The shared log format made ocean weather records comparable, and the meeting is often called the first international oceanographic conference.",
    ),

    entry(
        databaseId = 19,
        kind = Kind.PRACTICE,
        section = "Electrical measurement",
        title = "Weber and Kohlrausch's electrical units",
        originalTitle = "Elektrodynamische Maassbestimmungen (1856)",
        author = "Wilhelm Weber and Rudolf Kohlrausch",
        year = 1856,
        summary = "Weber and Kohlrausch measured the ratio between electrostatic and electromagnetic units, and the ratio came out close to the speed of light.",
        details = "Their 1856 experiment gave the ratio as a speed of about 310,000 kilometres per second, and Maxwell later noted how close it was to the speed of light. The measurement gave electrical units a common physical basis across laboratories.",
    ),

    entry(
        databaseId = 19,
        kind = Kind.PRACTICE,
        section = "Standards",
        title = "British Weights and Measures Act of 1855",
        originalTitle = "Weights and Measures Act (1855)",
        author = "Parliament of the United Kingdom",
        year = 1855,
        summary = "The Act set up new standards for the yard and the pound in the United Kingdom, giving the country one legal reference for measures.",
        details = "The old standards had been destroyed in the 1834 fire at the Palace of Westminster, and the Act provided replacements. The reference standards gave every measuring trade one legal basis for length and weight.",
    ),

    entry(
        databaseId = 19,
        kind = Kind.PRACTICE,
        section = "Codes for reading",
        title = "Braille's six-dot cell adopted in Paris",
        originalTitle = "Procede pour ecrire les paroles, la musique et le plain-chant (1829)",
        author = "Louis Braille",
        year = 1854,
        summary = "Braille's six-dot cell was adopted as the official reading and writing system of the Paris institute for the blind in 1854.",
        details = "Louis Braille devised the code and published it in 1829, and the institute adopted it officially in 1854, so the record dates the adoption. The six-dot cell allows 63 non-empty dot patterns, a binary-style code of six positions.",
    ),

    entry(
        databaseId = 19,
        kind = Kind.PRACTICE,
        section = "Electrical units",
        title = "Siemens's mercury unit of resistance",
        originalTitle = "Mercury unit of electrical resistance (1860)",
        author = "Werner von Siemens",
        year = 1860,
        summary = "Siemens proposed a practical unit of electrical resistance defined by a column of mercury, so that laboratories could reproduce the same standard.",
        details = "The unit was the resistance of a mercury column one metre long and one square millimetre in section, and telegraph engineers used such standards to compare their line measurements. It was later superseded by the ohm as national standards were set up.",
    ),
)
