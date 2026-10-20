package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 330 to 340: a road book to Jerusalem, Firmicus Maternus's astrology in eight books, and two Greek works by Eusebius on Constantine. */
val db171Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 171,
        kind = Kind.LANGUAGE,
        section = "Latin",
        title = "The Bordeaux pilgrim's road book to Jerusalem, a list of stations",
        originalTitle = "Itinerarium Burdigalense",
        author = "An anonymous pilgrim from Bordeaux",
        year = 333,
        summary = "An anonymous Christian from Bordeaux wrote down the road from Gaul to Jerusalem in 333 as a list of stations with the distances between them.",
        details = "The text gives the stations of the route and the distances between them in Roman miles, and it names the holy places the pilgrim saw in Jerusalem. The year 333 is the date usually given for the journey, and the text survives only in medieval copies.",
        rare = true,
    ),

    entry(
        databaseId = 171,
        kind = Kind.LANGUAGE,
        section = "Latin",
        title = "Firmicus Maternus's Mathesis, an astrology handbook in eight books",
        originalTitle = "Matheseos libri VIII",
        author = "Julius Firmicus Maternus",
        year = 334,
        summary = "Firmicus Maternus wrote the Mathesis, a Latin handbook of astrology in eight books, in the 330s.",
        details = "The work is dedicated to Lollianus Mavortius, a Roman official whose office the sources describe in different ways. Most accounts date its composition to 334 to 337, and the year used here is the first of those, while some accounts put its completion nearer 350. The title says eight books, but one account counts an introduction and seven books, so the count depends on how the parts are reckoned.",
        rare = true,
    ),

    entry(
        databaseId = 171,
        kind = Kind.LANGUAGE,
        section = "Greek",
        title = "Eusebius's Tricennial Oration in praise of Constantine",
        originalTitle = "Triakontaeterikos",
        author = "Eusebius of Caesarea",
        year = 336,
        summary = "Eusebius's Greek speech in praise of Constantine marked the thirtieth year of the emperor's reign, and it is usually dated to 336.",
        details = "The speech was given in the emperor's presence at Constantinople, and the Life of Constantine describes it as already delivered when that work was written. The year 336 is the one usually given for it.",
        rare = true,
    ),

    entry(
        databaseId = 171,
        kind = Kind.LANGUAGE,
        section = "Greek",
        title = "Eusebius's Life of the Blessed Emperor Constantine, a Greek biography",
        originalTitle = "Ton makarion basileos Konstantinou bios",
        author = "Eusebius of Caesarea",
        year = 339,
        summary = "Eusebius wrote a Greek life of Constantine after the emperor's death in 337, praising his faith and his rule.",
        details = "Accounts date its composition to the years after 337, and Eusebius probably died around 339 to 340, so 339 is the year used here within that spread. The work is the main source for Constantine's conversion story.",
    ),

)
