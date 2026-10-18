package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 1271 and 1275: Yang Hui's magic figures and Tusi's Ilkhanic astronomical tables. */
val db77Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 77,
        kind = Kind.TOOL,
        section = "Mathematics",
        title = "Yang Hui's Xugu zhaiqi suanfa, a Chinese work on magic figures",
        originalTitle = "Xugu zhaiqi suanfa",
        author = "Yang Hui",
        year = 1275,
        summary = "Yang Hui's Xugu zhaiqi suanfa, a Chinese mathematical work of about 1275, is devoted mainly to magic squares and magic circles.",
        details = "The title is often rendered as Selection of Strange Mathematical Methods in Continuation of Antiquity. Accounts differ on Yang Hui's dates, with one giving a life from about 1238 to 1298 and another placing his known activity between about 1261 and 1275.",
        rare = true,
    ),

    entry(
        databaseId = 77,
        kind = Kind.TOOL,
        section = "Astronomy",
        title = "Tusi's Ilkhanic zij, a set of astronomical tables made at Maragha",
        originalTitle = "Zij-i Ilkhani",
        author = "Nasir al-Din al-Tusi",
        year = 1271,
        summary = "Nasir al-Din al-Tusi's Zij-i Ilkhani is a set of astronomical tables completed in the early 1270s for the Ilkhans' observatory at Maragha.",
        details = "Accounts give 1271 or 1272 as the year of completion, and the tables were copied and used across the Islamic east for generations. The tables were worked out from observations made at Maragha.",
        rare = true,
    ),

)
