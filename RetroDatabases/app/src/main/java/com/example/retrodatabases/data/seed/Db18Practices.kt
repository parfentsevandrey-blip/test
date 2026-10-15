package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 1860 to 1870: agreements on atomic weights, statistics, metric units, electrical units and telegraph rules. */
val db18Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 18,
        kind = Kind.PRACTICE,
        section = "Atomic weights",
        title = "Karlsruhe Congress on atomic weights",
        originalTitle = "Internationaler Chemiker-Congress, Karlsruhe (1860)",
        author = "International Chemical Congress, Karlsruhe",
        year = 1860,
        summary = "Chemists met at Karlsruhe to settle how atomic and molecular weights should be used, so that formulas from different laboratories could be compared.",
        details = "The congress met in September 1860, and Cannizzaro's argument for molecular weights from gas densities was read there and circulated. The meeting did not settle every value, so the disputes went on after it.",
    ),

    entry(
        databaseId = 18,
        kind = Kind.PRACTICE,
        section = "Statistics",
        title = "International Statistical Congress, London session",
        originalTitle = "International Statistical Congress, session in London (1860)",
        author = "International Statistical Congress",
        year = 1860,
        summary = "A meeting of government statisticians from several countries that worked toward common methods for counting people, trade and other national figures.",
        details = "The congress had met since 1853 and held its London session in 1860. Its aim was to make census and trade figures comparable between countries, an early step toward the international statistical standards of later decades.",
    ),

    entry(
        databaseId = 18,
        kind = Kind.PRACTICE,
        section = "Weights and measures",
        title = "North German metric law of 1868",
        originalTitle = "Gesetz betreffend Mass und Gewicht, North German Confederation (1868)",
        author = "North German Confederation",
        year = 1868,
        summary = "A law made the metre and the kilogram the legal units across the North German states, with the change to take effect in 1872.",
        details = "The law was passed on 17 August 1868 and came into force in 1872. It prepared Germany for the international metric arrangements of the 1875 Metre Convention.",
    ),

    entry(
        databaseId = 18,
        kind = Kind.PRACTICE,
        section = "Electrical standards",
        title = "British Association unit of electrical resistance",
        originalTitle = "Report of the British Association committee on electrical standards (1863)",
        author = "British Association for the Advancement of Science",
        year = 1863,
        summary = "A committee of the British Association fixed a common unit of electrical resistance, so that wire resistances measured in different laboratories could be compared.",
        details = "The committee's report of 1863 set out the unit, known as the B.A. unit. Before a common unit was fixed, laboratories used units that differed from one another, which made their measurements hard to compare.",
    ),

    entry(
        databaseId = 18,
        kind = Kind.PRACTICE,
        section = "Telegraph conventions",
        title = "International Telegraph Union, the Paris convention",
        originalTitle = "International Telegraph Convention, Paris (1865)",
        author = "International Telegraph Conference, Paris",
        year = 1865,
        summary = "A treaty among European states set common telegraph rules, charges and routing, and created a standing body that grew into the modern international telecommunications union.",
        details = "The convention was signed in Paris in 1865, at the same conference that adopted the revised Morse alphabet, which is a separate record in this database. The union it created is the forerunner of the International Telecommunication Union.",
    ),

)
