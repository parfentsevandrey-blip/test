package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 1640 to 1650: a mountain barometer test, a letter network, two calendars in one letter, open-sight observing and a dated chronology. */
val db40Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 40,
        kind = Kind.PRACTICE,
        section = "Pressure",
        title = "Perier's barometers on the Puy de Dome",
        originalTitle = "Recit de la grande experience de l'equilibre des liqueurs",
        author = "Florin Perier",
        year = 1648,
        summary = "Pascal's brother-in-law carried mercury barometers up the Puy de Dome and found the column shorter at the top, which showed that the air's pressure falls with height.",
        details = "The climb took place on 19 September 1648, and the barometer fell by about three inches between the foot and the summit. A second barometer was kept at the foot as a control, so that a change in the weather could be told apart from a change in height.",
    ),

    entry(
        databaseId = 40,
        kind = Kind.PRACTICE,
        section = "Clocks",
        title = "Italian hours counted from sunset",
        originalTitle = "Ore italiche",
        author = "Clock keepers of Italian towns",
        year = 1640,
        summary = "Italian towns counted the day from sunset, so every public clock had to be set again each evening to show the hours of the new day.",
        details = "The system is medieval in origin, so 1640 names a period of use in Florence rather than the first written form. The twenty-four hours ran from one sunset to the next, so the hour shown by a clock changed with the season.",
    ),

    entry(
        databaseId = 40,
        kind = Kind.PRACTICE,
        section = "Dating",
        title = "Dating letters across two calendars in the 1640s",
        originalTitle = "Old Style and New Style dates in letters",
        author = "Anonymous letter writers",
        year = 1640,
        summary = "English letters of the 1640s began the year on 25 March and kept the Julian calendar, so a letter sent across the Channel could carry two different dates.",
        details = "Continental correspondents used the Gregorian calendar, which was ten days ahead of the Julian one in the seventeenth century, so writers on both sides had to agree on which calendar a date used. The practice shows that the calendar reform of 1582 was still a matter of daily correspondence in the 1640s.",
    ),

    entry(
        databaseId = 40,
        kind = Kind.PRACTICE,
        section = "Observing",
        title = "Hevelius's open-sight observing at Danzig",
        originalTitle = "Stellaburg observatory",
        author = "Johannes Hevelius",
        year = 1641,
        summary = "Hevelius observed the sky from a rooftop observatory in Danzig, and he preferred open sights to telescopes for measuring the positions of stars.",
        details = "He built the Stellaburg in 1641 on the roofs of his brewery houses, and he worked with long instruments and sights rather than telescope micrometers. The practice is a reminder that accuracy depended on the observer's care and on the instruments he chose, not only on the newest optics.",
    ),

    entry(
        databaseId = 40,
        kind = Kind.PRACTICE,
        section = "Chronology",
        title = "Ussher's Annals, dating events back to creation",
        originalTitle = "Annales veteris testamenti, a prima mundi origine deducti",
        author = "James Ussher",
        year = 1650,
        summary = "Ussher reconciled biblical and ancient histories into one dated chronology, which placed creation in 4004 BC for generations of readers.",
        details = "The first part of the Annals was printed in London in 1650, and later editions carried the year-by-year dating. The 4004 BC date later appeared in the margins of Bibles, and it was widely taken as Ussher's own figure for the start of the world.",
    ),

)
