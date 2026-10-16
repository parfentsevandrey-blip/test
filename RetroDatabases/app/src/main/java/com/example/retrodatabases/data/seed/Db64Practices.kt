package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 1400 to 1403: reign-year dating, plainchant neumes, paper watermarks, numbered leaves and candle clocks. */
val db64Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 64,
        kind = Kind.PRACTICE,
        section = "Dating",
        title = "Dating a year by the reign of the Yongle emperor",
        originalTitle = "Yongle reign years",
        author = "The Ming court",
        year = 1403,
        summary = "Ming documents from 1403 onward were dated by the reign years of the Yongle emperor, so each year was named by its place in his reign.",
        details = "The first year of the Yongle reign is 1403 in the usual reckoning, and officials gave the regnal count alongside the sexagenary name. Turning a reign year into a Western year needs a short table, and each new reign began a new count.",
    ),

    entry(
        databaseId = 64,
        kind = Kind.PRACTICE,
        section = "Music",
        title = "Neumes, the signs of plainchant written over the words",
        originalTitle = "Neumes",
        author = "Scribes of the chant books",
        year = 1400,
        summary = "By about 1400 the neumes of plainchant were written over the words of a hymn, and their shapes showed how the melody rose, fell or repeated.",
        details = "The early neumes showed the shape of a melody more clearly than its exact pitch, which is why later staff notation was needed. Accounts differ on how precisely neumes fixed the pitch, and the year 1400 is a round date for the period.",
    ),

    entry(
        databaseId = 64,
        kind = Kind.PRACTICE,
        section = "Paper",
        title = "Watermarks, the wire designs that marked the sheet",
        originalTitle = "Watermark",
        author = "Paper makers of Italy and France",
        year = 1400,
        summary = "By about 1400 many papers made in Italy and France carried watermarks, wire designs in the mould that marked the sheet and often named its maker.",
        details = "A watermark was bent from wire and sewn onto the mould, so it showed as a lighter pattern when the sheet was held to the light. Accounts differ on when such marks first appeared, but by 1400 they were common enough to help identify the mill that made a sheet.",
    ),

    entry(
        databaseId = 64,
        kind = Kind.PRACTICE,
        section = "Manuscripts",
        title = "Numbering the leaves of a manuscript, the old foliation",
        originalTitle = "Foliation",
        author = "Scribes and librarians",
        year = 1400,
        summary = "By about 1400 many manuscripts had their leaves numbered in a corner, so that a reader could cite a leaf and find it again.",
        details = "The numbers were written by the scribe or added later by a librarian, and accounts differ on when the practice became general. The year 1400 is a round date for the period.",
    ),

    entry(
        databaseId = 64,
        kind = Kind.PRACTICE,
        section = "Time",
        title = "Candle clocks marked with rings for the hours",
        originalTitle = "Candle clock",
        author = "Householders and monks of medieval Europe",
        year = 1400,
        summary = "Candle clocks, candles marked with rings that burned down at a known rate, were in use in medieval Europe by about 1400.",
        details = "The hours were read from how far the flame had burned past each ring, and the rate of burning had to stay steady for the reading to be right. Accounts differ on how accurate such candles were and how long the method lasted beside mechanical clocks.",
    ),

)
