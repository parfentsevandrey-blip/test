package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 1580 to 1590: counting a cipher, chronograms in Latin mottoes, the art of memory, the dominical letter and the new crescent. */
val db46Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 46,
        kind = Kind.PRACTICE,
        section = "Codebreaking",
        title = "Phelippes's counts in the Babington letters",
        originalTitle = "Phelippes's counts in the Babington letters",
        author = "Thomas Phelippes",
        year = 1586,
        summary = "Thomas Phelippes read the Babington letters of 1586 by counting the symbols of the nomenclator and comparing their frequencies with those of English letters.",
        details = "The letters passed between Anthony Babington and Mary, Queen of Scots, in a nomenclator cipher that mixed letters, code symbols and filler marks. Accounts differ on how much of the reading came from counting and how much from a forged postscript that drew out the names of the plotters.",
        rare = true,
    ),

    entry(
        databaseId = 46,
        kind = Kind.PRACTICE,
        section = "Inscriptions",
        title = "Chronograms: a year hidden in a Latin motto",
        originalTitle = "Chronograms: a year hidden in a Latin motto",
        author = "Latin inscription makers",
        year = 1586,
        summary = "A chronogram hides a year in a motto by marking the letters that are Roman numerals, so that adding their values gives the date.",
        details = "The practice was common in Latin inscriptions and printed books of the later sixteenth century, and a reader adds the marked letters to find the year. Makers often set the numeral letters in capitals so that the year stands out in the motto.",
    ),

    entry(
        databaseId = 46,
        kind = Kind.PRACTICE,
        section = "Memory",
        title = "Bruno's memory images, De umbris idearum",
        originalTitle = "De umbris idearum",
        author = "Giordano Bruno",
        year = 1582,
        summary = "Bruno's De umbris idearum of 1582 taught a memory art built on images placed in order, so that long lists could be recalled from a fixed set of places.",
        details = "The book was printed in Paris in 1582 and joins the art of memory to a view of knowledge as the shadow of ideas. Historians differ on how far its method was new and how far it follows the older art of places.",
        rare = true,
    ),

    entry(
        databaseId = 46,
        kind = Kind.PRACTICE,
        section = "Calendar",
        title = "Finding the dominical letter for weekdays",
        originalTitle = "Finding the dominical letter for weekdays",
        author = "Church calendar makers",
        year = 1582,
        summary = "The dominical letter marks the days of the week on which Sundays fall, and a clerk read it from a table, which the reform of 1582 had to revise.",
        details = "The letters were used in church calendars long before 1582, and the Gregorian reform needed a new table because its leap years differ from those of the Julian calendar. The practice is a table lookup rather than a single fixed date.",
    ),

    entry(
        databaseId = 46,
        kind = Kind.PRACTICE,
        section = "Observation",
        title = "Watching for the new crescent of the Islamic month",
        originalTitle = "Watching for the new crescent of the Islamic month",
        author = "Islamic calendar makers",
        year = 1580,
        summary = "An Islamic month begins when the new crescent is first seen, so calendar makers of the period watched the western sky after each new moon.",
        details = "The rule of the crescent sighting is older than 1580 and is still used in parts of the Islamic world. The Istanbul observatory of the same years was one place where calendar and astronomical work was done for the Ottoman court.",
    ),

)
