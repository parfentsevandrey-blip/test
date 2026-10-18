package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 1200 to 1210: two English chancery series of copied letters from 1201 and 1204, and Venice's large silver coin of 1202. */
val db84Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 84,
        kind = Kind.PRACTICE,
        section = "England",
        title = "The English patent rolls, copies of open royal letters from 1201",
        originalTitle = "Rotuli litterarum patentium",
        author = "The chancery of King John",
        year = 1201,
        summary = "The patent rolls copied the open royal letters of the English chancery, and the oldest surviving series begins in King John's reign in 1201.",
        details = "Each copied letter was one that carried the Great Seal hanging from it, so the rolls record decisions the king meant to be public. The series has run to the present, and its gaps are mostly in the mid-seventeenth century, during the Civil War and the Interregnum.",
        rare = true,
    ),

    entry(
        databaseId = 84,
        kind = Kind.PRACTICE,
        section = "England",
        title = "The English close rolls, copies of sealed royal letters from 1204",
        originalTitle = "Rotuli litterarum clausarum",
        author = "The chancery of King John",
        year = 1204,
        summary = "The close rolls copied the sealed royal letters of the English chancery, and the oldest surviving roll dates from King John's reign in 1204.",
        details = "Letters close were folded and sealed, so their contents went only to the person they were sent to, and the chancery kept copies in the rolls. The practice may be older than the oldest roll, possibly from about 1200 or earlier, and the series ran for centuries.",
        rare = true,
    ),

    entry(
        databaseId = 84,
        kind = Kind.PRACTICE,
        section = "Venice",
        title = "The Venetian grosso, a large silver coin struck from 1202",
        originalTitle = "Grosso veneziano",
        author = "Enrico Dandolo, doge of Venice",
        year = 1202,
        summary = "The grosso was a large silver coin that Venice began to strike in 1202 under Doge Enrico Dandolo, and other mints later copied its size and type.",
        details = "The coin was a silver piece far larger than the small penny in use in most of Europe at the time. Its first striking is usually dated to 1202, and that is the year given here.",
        rare = true,
    ),

)
