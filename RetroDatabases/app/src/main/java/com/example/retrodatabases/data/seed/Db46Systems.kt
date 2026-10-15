package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 1580 to 1590: the Gregorian calendar, Tycho's observatory, Hideyoshi's land survey, the Armada beacon chain and the Vatican printing house. */
val db46Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 46,
        kind = Kind.SYSTEM,
        section = "Calendar",
        title = "The Gregorian calendar, the bull Inter gravissimas",
        originalTitle = "Inter gravissimas",
        author = "Pope Gregory XIII",
        year = 1582,
        summary = "Gregory XIII's bull of 1582 set up a calendar that dropped ten days in October 1582 and skipped leap days in century years that are not divisible by 400.",
        details = "The bull is dated 24 February 1582. Catholic states such as Spain, Portugal and Italy began the new count in October 1582, when Thursday 4 October was followed by Friday 15 October, while Protestant and Orthodox lands kept the Julian calendar for much longer. The reform drew on the work of Christopher Clavius and his colleagues in Rome.",
    ),

    entry(
        databaseId = 46,
        kind = Kind.SYSTEM,
        section = "Observatories",
        title = "Tycho's observatory Uraniborg on Hven",
        originalTitle = "Uraniborg",
        author = "Tycho Brahe",
        year = 1580,
        summary = "Uraniborg was the observatory that Tycho Brahe built on the island of Hven, with rooms for instruments, a laboratory and a library, and it was in full use by about 1580.",
        details = "Tycho began to build Uraniborg in 1576, and the site gave him the instruments and staff for his star observations. A second site, Stjerneborg, partly sunk into the ground, was added in 1584, and the two together made Hven a centre of observation in Europe.",
    ),

    entry(
        databaseId = 46,
        kind = Kind.SYSTEM,
        section = "Land",
        title = "Hideyoshi's land survey begun in 1582",
        originalTitle = "Taiko kenchi",
        author = "Toyotomi Hideyoshi",
        year = 1582,
        summary = "Hideyoshi began a survey of the land of Japan in 1582, measuring fields by standard units so that their yields could be assessed.",
        details = "The survey was carried out region by region from 1582, with standard lengths and areas set for measuring fields and for rating their rice yield. Accounts differ on how far the survey reached in each province, so the record gives only its aim and its start.",
        rare = true,
    ),

    entry(
        databaseId = 46,
        kind = Kind.SYSTEM,
        section = "Signals",
        title = "The Armada beacon chain of 1588",
        originalTitle = "The Armada beacon chain of 1588",
        author = "The English Privy Council",
        year = 1588,
        summary = "In 1588 the English prepared a chain of hilltop beacons that would carry a warning from the coast inland when the Spanish Armada was sighted.",
        details = "Watchmen were set to fire the beacons in turn, so that a warning crossed the country faster than a messenger could ride. Accounts differ on how many beacons were set and where, so the record gives only the general plan.",
    ),

    entry(
        databaseId = 46,
        kind = Kind.SYSTEM,
        section = "Printing",
        title = "The Vatican printing house of Sixtus V",
        originalTitle = "Typographia Vaticana",
        author = "Pope Sixtus V",
        year = 1587,
        summary = "Sixtus V set up a printing house in the Vatican in 1587 to print the books of the Church.",
        details = "The press was set up in 1587 within the Vatican to print church books, and its editions of the Scriptures were meant to be authoritative. It was part of the Counter-Reformation effort to control the text of the books that the Church used.",
    ),

)
