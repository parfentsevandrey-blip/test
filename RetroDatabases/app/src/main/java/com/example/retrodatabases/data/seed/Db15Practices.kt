package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 1890 to 1900: a fitting method for frequency curves, units, wiring rules, a congress and a cloud atlas. */
val db15Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 15,
        kind = Kind.PRACTICE,
        section = "Frequency curves",
        title = "Pearson's method of moments and skew curves",
        originalTitle = "Contributions to the Mathematical Theory of Evolution, I: On the dissection of asymmetrical frequency-curves (1894)",
        author = "Karl Pearson",
        year = 1894,
        summary = "A way of fitting frequency curves by matching their moments to the sample moments, which gives a family of skew curves.",
        details = "Pearson's 1894 memoir introduced the method of moments for fitting asymmetric curves, and the family of curves it produced bears his name. It is a different method from Pearson's chi-square test of 1900, which is a separate record of database 14.",
    ),

    entry(
        databaseId = 15,
        kind = Kind.PRACTICE,
        section = "Units",
        title = "Mendenhall Order on United States units",
        originalTitle = "Mendenhall Order, US Treasury (1893)",
        author = "Thomas Corwin Mendenhall",
        year = 1893,
        summary = "An order of the United States Treasury that defined the customary units of weight and length in terms of metric standards.",
        details = "Mendenhall, then superintendent of weights and measures, issued the order in July 1893, tying the pound and the yard to the kilogram and the metre. It marks the start of the United States practice of defining customary units through metric standards.",
    ),

    entry(
        databaseId = 15,
        kind = Kind.PRACTICE,
        section = "Wiring rules",
        title = "First National Electrical Code",
        originalTitle = "National Electrical Code, first edition (1897)",
        author = "National Board of Fire Underwriters",
        year = 1897,
        summary = "A set of rules for electrical wiring, drafted by insurers and electrical bodies, which became the standard code for building safety.",
        details = "The first edition appeared in 1897, and its rules were drafted by a national conference of insurers and electrical bodies. Accounts differ on the month, with an April 1897 draft and an August edition both named, so the first edition's date is disputed.",
    ),

    entry(
        databaseId = 15,
        kind = Kind.PRACTICE,
        section = "Meetings",
        title = "First International Congress of Mathematicians, Zurich",
        originalTitle = "Verhandlungen des Ersten Internationalen Mathematiker-Kongresses (1897)",
        author = "Organising committee of the Zurich congress",
        year = 1897,
        summary = "The first meeting of the International Congress of Mathematicians, held at Zurich in 1897, brought the field together for shared reports.",
        details = "The congress met at Zurich in August 1897, and its proceedings were printed the same year. Hilbert's address at the Paris congress of 1900 is a separate record of database 14.",
    ),

    entry(
        databaseId = 15,
        kind = Kind.PRACTICE,
        section = "Weather classification",
        title = "International Cloud Atlas",
        originalTitle = "Atlas international des nuages (1896)",
        author = "Hugo Hildebrandsson, Albert Riggenbach and Leon Teisserenc de Bort",
        year = 1896,
        summary = "An atlas of cloud types with coloured plates and a classification, which became one of the standard guides for observing clouds at weather stations.",
        details = "The atlas was issued in Paris by Gauthier-Villars in 1896, with text in English, French and German. Later editions are dated differently in different accounts, with 1906 and 1911 among the dates given.",
    ),
)
