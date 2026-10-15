package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 1780 to 1790: a national triangulation, an almanac for navigators, a decimal money system and a steam-powered mint. */
val db26Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 26,
        kind = Kind.SYSTEM,
        section = "Surveying",
        title = "Roy's Principal Triangulation of Britain",
        originalTitle = "Principal Triangulation of Great Britain",
        author = "William Roy",
        year = 1784,
        summary = "A chain of triangles across Britain, begun from a measured base on Hounslow Heath, gave the country one frame of coordinates.",
        details = "The base was measured in 1784. The triangulation became the basis of the Ordnance Survey maps for the rest of the century.",
    ),

    entry(
        databaseId = 26,
        kind = Kind.SYSTEM,
        section = "Navigation",
        title = "The Nautical Almanac with lunar distance tables",
        originalTitle = "The Nautical Almanac and Astronomical Ephemeris",
        author = "Nevil Maskelyne and the Board of Longitude",
        year = 1783,
        summary = "A yearly almanac whose tables let a navigator find longitude from the Moon's distance from a star.",
        details = "The almanac began in 1767 under Maskelyne, the Astronomer Royal. By the 1780s its lunar distance tables were in common use at sea, together with a sextant and a set of calculations.",
    ),

    entry(
        databaseId = 26,
        kind = Kind.SYSTEM,
        section = "Money",
        title = "Jefferson's decimal dollar",
        originalTitle = "Notes on the establishment of a money unit, and of a coinage for the United States",
        author = "Thomas Jefferson",
        year = 1785,
        summary = "A money system in which the dollar is divided into tenths and hundredths, so that every amount is a decimal number.",
        details = "Jefferson proposed the plan in 1784, and Congress adopted the dollar as the money unit in 1785. Decimal coinage later became the model for many other currencies.",
    ),

    entry(
        databaseId = 26,
        kind = Kind.SYSTEM,
        section = "Manufacturing",
        title = "Boulton's steam-powered Soho Mint",
        originalTitle = "Soho Mint, Birmingham",
        author = "Matthew Boulton and James Watt",
        year = 1788,
        summary = "A coining works where steam engines drove the presses, so that coins were made by one standard process.",
        details = "The mint near Birmingham began coining in 1788 with steam-driven machinery. Its standard parts and steady output were early examples of mass production.",
        rare = true,
    ),

    entry(
        databaseId = 26,
        kind = Kind.SYSTEM,
        section = "Surveying",
        title = "Cassini's survey of France by triangulation",
        originalTitle = "Carte geometrique de la France",
        author = "The Cassini family",
        year = 1785,
        summary = "A national survey of France by a chain of triangles, begun in 1747 and still being published in the 1780s.",
        details = "The survey covered the whole kingdom and was one of the largest mapping projects of the eighteenth century. It showed how a triangulation could give a country one set of coordinates.",
    ),
)
