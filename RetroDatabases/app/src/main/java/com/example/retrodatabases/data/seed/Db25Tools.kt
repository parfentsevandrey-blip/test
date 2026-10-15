package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 1790 to 1800: printed tables and dictionaries, the platinum standards, a grade scale for angles and a navigation handbook. */
val db25Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 25,
        kind = Kind.TOOL,
        section = "Logarithms",
        title = "Callet's logarithm tables",
        originalTitle = "Tables portatives de logarithmes (1795)",
        author = "Jean-Francois Callet",
        year = 1795,
        summary = "A pocket table of logarithms of numbers and of the sines, cosines and tangents, compiled from earlier English tables and printed in Paris.",
        details = "The book was printed in Paris under the Didot imprint in 1795, and it drew on William Gardiner's earlier tables. Reissues kept appearing for more than a century, and the catalogue records give 1906 as the date of the last issue, while page counts vary between records.",
    ),

    entry(
        databaseId = 25,
        kind = Kind.TOOL,
        section = "Standards",
        title = "The platinum metre and kilogram of the Archives",
        originalTitle = "Metre and kilogram des Archives (1799)",
        author = "French Republic",
        year = 1799,
        summary = "A platinum bar for the metre and a platinum cylinder for the kilogram were made to hold the new units in one physical form.",
        details = "The two standards were deposited in the Archives in Paris in 1799, after the decimal units had been defined by law. Later comparisons with these standards helped set the length and mass of the metric system for many countries.",
    ),

    entry(
        databaseId = 25,
        kind = Kind.TOOL,
        section = "Reference",
        title = "Hutton's mathematical dictionary",
        originalTitle = "A Mathematical and Philosophical Dictionary (1795 to 1796)",
        author = "Charles Hutton",
        year = 1795,
        summary = "A two-volume dictionary of mathematical and philosophical terms, with definitions and tables for students and practical men in Britain.",
        details = "The dictionary appeared in London in two volumes in 1795 and 1796. It collected Hutton's articles on mathematics and natural philosophy, and it drew on his earlier logarithm tables, which had been widely used in Britain.",
    ),

    entry(
        databaseId = 25,
        kind = Kind.TOOL,
        section = "Tables",
        title = "Prony's cadastral tables",
        originalTitle = "Tables du cadastre (1790s)",
        author = "Gaspard de Prony",
        year = 1799,
        summary = "Large tables of logarithms and trigonometric values, computed by Prony's office for the French land survey and built up by differences.",
        details = "The tables were made for the cadastre, the survey of land for taxation, in the 1790s while Prony's computing office was at work. This record dates them to 1799.",
        rare = true,
    ),

    entry(
        databaseId = 25,
        kind = Kind.TOOL,
        section = "Angles",
        title = "The centesimal grade",
        originalTitle = "Grade, one hundredth of a right angle (1795)",
        author = "French National Convention",
        year = 1795,
        summary = "The right angle was divided into 100 grades, so that a full circle held 400, a decimal unit for measuring angles in surveys.",
        details = "The grade was part of the French decimal reform of the 1790s, which also gave the metre and the gram. Instrument makers engraved grade scales on theodolites and quadrants, and surveyors used them for decimal angle sums.",
        rare = true,
    ),

    entry(
        databaseId = 25,
        kind = Kind.TOOL,
        section = "Navigation",
        title = "Lalande's navigation abridgment",
        originalTitle = "Abrege de navigation historique, theorique et pratique (1793)",
        author = "Joseph Jerome Lefrancois de Lalande",
        year = 1793,
        summary = "A compact handbook for sea navigators, setting out the methods of finding a ship's place by the sun, the stars and the log.",
        details = "Lalande published the abridgment in Paris in 1793 as a short guide to navigation, covering history and theory as well as practice. This record dates the work to its first edition of 1793.",
    ),
)
