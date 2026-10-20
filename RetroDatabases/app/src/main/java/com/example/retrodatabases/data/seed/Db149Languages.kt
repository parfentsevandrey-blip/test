package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 550 to 560: Wei Shou's Book of Wei, completed in 554, and Jordanes's Getica, written in 551. */
val db149Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 149,
        kind = Kind.LANGUAGE,
        section = "Chinese",
        title = "Wei Shou's Book of Wei, the dynastic history of the Northern Wei, completed in 554",
        originalTitle = "Weishu",
        author = "Wei Shou",
        year = 554,
        summary = "Wei Shou's Book of Wei, a history of the Northern Wei and Eastern Wei from 386 to 550, was completed in 554 and later joined the Twenty-Four Histories.",
        details = "Wei Shou (506 to 572) compiled it under the Northern Qi, and encyclopedias date the work to the years 551 to 554. One scholarly source puts the formal submission in 555, and the scroll count is given as 114 in some places and 130 in others.",
        rare = true,
    ),

    entry(
        databaseId = 149,
        kind = Kind.LANGUAGE,
        section = "Latin",
        title = "Jordanes's Getica, a Latin history of the Goths written in Constantinople in 551",
        originalTitle = "De origine actibusque Getarum",
        author = "Jordanes",
        year = 551,
        summary = "Jordanes wrote the Getica in Constantinople in 551, a short Latin history of the Goths that condenses the lost Gothic history of Cassiodorus.",
        details = "The work runs from the origins of the Goths to the time of Justinian and is built on a longer history in twelve books by Cassiodorus, which does not survive. Jordanes's claims about Gothic origins and his use of older sources are disputed, so the book is read as a summary of earlier material rather than as an independent record.",
        rare = true,
    ),

)
