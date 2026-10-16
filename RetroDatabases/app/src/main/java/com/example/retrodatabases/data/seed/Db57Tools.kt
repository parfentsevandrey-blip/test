package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 1470 to 1480: Regiomontanus's ephemeris and calendar, the Treviso arithmetic, Crescenzi's farming book, Valturio's war machines and Pliny in roman type. */
val db57Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 57,
        kind = Kind.TOOL,
        section = "Astronomy tables",
        title = "Regiomontanus's Ephemerides for 1475 to 1506",
        originalTitle = "Ephemerides astronomicae",
        author = "Johannes Regiomontanus",
        year = 1474,
        summary = "Regiomontanus printed at Nuremberg in 1474 a table of the daily positions of the planets for the years from 1475 to 1506.",
        details = "The ephemeris is a table book, and the story that Columbus used it to predict a lunar eclipse in 1504 is widely told, though accounts differ on the details. Regiomontanus died at Rome in 1476, two years after the book was printed.",
    ),

    entry(
        databaseId = 57,
        kind = Kind.TOOL,
        section = "Commercial arithmetic",
        title = "The Treviso Arithmetic, the earliest printed commercial arithmetic",
        originalTitle = "Larte de labbacho",
        author = "Anonymous",
        year = 1478,
        summary = "The Treviso Arithmetic of 1478, an anonymous Italian book for merchants, is usually called the earliest printed commercial arithmetic.",
        details = "It was printed at Treviso and taught the sums that merchants met in trade, written in the Italian of the abbaco schools. The author is not named, and accounts differ on whether it is the earliest printed book of its kind.",
    ),

    entry(
        databaseId = 57,
        kind = Kind.TOOL,
        section = "Farming",
        title = "Crescenzi's farming book, printed at Augsburg in 1471",
        originalTitle = "Ruralia commoda",
        author = "Pier de' Crescenzi",
        year = 1471,
        summary = "Crescenzi's Latin handbook on estates, vines and gardens was printed at Augsburg in 1471, and it is one of the earliest printed farming books.",
        details = "The treatise was written by a Bolognese lawyer and completed about 1300 or 1305, and accounts give the Augsburg printing as February or March 1471. Some accounts call it the first printed farming book, a claim that depends on how earlier printing is counted.",
    ),

    entry(
        databaseId = 57,
        kind = Kind.TOOL,
        section = "Military engineering",
        title = "Valturio's De re militari, the book of war machines",
        originalTitle = "De re militari",
        author = "Roberto Valturio",
        year = 1472,
        summary = "Valturio's book on military affairs, first printed at Verona in 1472, is illustrated with woodcuts of engines and war machines.",
        details = "The first printed edition came from the press of Johannes Nicolai de Verona, and some sources call it the first printed technical book with woodcut illustrations. Accounts give different counts of the woodcuts, so the record gives no number.",
    ),

    entry(
        databaseId = 57,
        kind = Kind.TOOL,
        section = "Calendars",
        title = "Regiomontanus's Calendarium, printed at Venice in 1476",
        originalTitle = "Kalendarium",
        author = "Johannes Regiomontanus",
        year = 1476,
        summary = "Regiomontanus's calendar, printed at Venice in 1476, gave the tables needed to date feasts and to reckon the moon's phases.",
        details = "The printing was done at Venice by Erhard Ratdolt, a German printer. Regiomontanus died at Rome in 1476, the year of the printing.",
    ),

    entry(
        databaseId = 57,
        kind = Kind.TOOL,
        section = "Natural history",
        title = "Pliny's Natural History printed by Jenson in 1472",
        originalTitle = "Naturalis historia",
        author = "Pliny the Elder",
        year = 1472,
        summary = "Jenson's Venetian edition of Pliny's Natural History, usually dated 1472, put the ancient encyclopedia of nature into roman type.",
        details = "The work has thirty-seven books on the natural world. The Venetian printing is usually dated 1472, and the record names the printer and the year only.",
    ),

)
