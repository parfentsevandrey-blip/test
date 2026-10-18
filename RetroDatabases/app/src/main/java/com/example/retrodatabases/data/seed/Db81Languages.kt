package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 1230 to 1235: a Norse history of kings, a Bavarian songbook, an Arabic world history, a Japanese Zen treatise and a Japanese anthology of one hundred poems. */
val db81Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 81,
        kind = Kind.LANGUAGE,
        section = "Old Norse",
        title = "Snorri Sturluson's Heimskringla, the sagas of the kings of Norway",
        originalTitle = "Heimskringla",
        author = "Snorri Sturluson",
        year = 1230,
        summary = "Snorri Sturluson wrote the Heimskringla in Old Norse prose in Iceland about 1230, a history of the kings of Norway from the legendary Yngling line to 1177.",
        details = "The title comes from the opening words of the book, kringla heimsins, the circle of the world. Its date is usually given as about 1230, though some accounts place the writing in the 1220s, and Snorri was murdered in 1241.",
    ),

    entry(
        databaseId = 81,
        kind = Kind.LANGUAGE,
        section = "Latin and German",
        title = "The Carmina Burana songbook, Latin and German songs from Bavaria",
        originalTitle = "Carmina Burana",
        author = "Anonymous poets, many of them goliards",
        year = 1230,
        summary = "The Carmina Burana is a Bavarian songbook of Latin and German songs, mostly secular and mostly anonymous, compiled about 1230.",
        details = "The manuscript was kept at the abbey of Benediktbeuern, was found there in 1803, and was published in 1847 by Johann Andreas Schmeller. The date of about 1230 is an estimate from the handwriting and the contents, so accounts differ on when the songs were first written down.",
    ),

    entry(
        databaseId = 81,
        kind = Kind.LANGUAGE,
        section = "Arabic",
        title = "Ibn al-Athir's al-Kamil fi al-tarikh, a universal history in Arabic",
        originalTitle = "al-Kamil fi al-tarikh",
        author = "Ibn al-Athir",
        year = 1231,
        summary = "Ibn al-Athir completed al-Kamil fi al-tarikh in 1231, a universal history in Arabic that runs from the earliest times to the years just before its writing.",
        details = "The work was written in Mosul and is dated to 628 of the Hijra, the year 1231 of the Christian era. Later historians used it as a standard account of the Islamic past, and it remains a main source for the period.",
        rare = true,
    ),

    entry(
        databaseId = 81,
        kind = Kind.LANGUAGE,
        section = "Japanese",
        title = "Dogen's Shobogenzo, the treasury of the true dharma eye, begun in 1231",
        originalTitle = "Shobogenzo",
        author = "Dogen",
        year = 1231,
        summary = "Dogen began his Shobogenzo, a collection of Zen essays and sermons, in 1231 and kept adding to it until his death in 1253.",
        details = "The essays are written in Japanese prose with many Chinese terms, and editions of the Shobogenzo arrange and count its fascicles in different ways. The date 1231 is the usual start of the work, and the span of its writing is given as 1231 to 1253.",
    ),

    entry(
        databaseId = 81,
        kind = Kind.LANGUAGE,
        section = "Japanese",
        title = "The Ogura Hyakunin Isshu, one hundred poems by one hundred poets",
        originalTitle = "Ogura Hyakunin Isshu",
        author = "Fujiwara no Teika, credited with the choice",
        year = 1235,
        summary = "The Ogura Hyakunin Isshu is a Japanese anthology of one hundred waka by one hundred poets, chosen about 1235 and named for Teika's villa at Ogura near Kyoto.",
        details = "The choice is credited to Fujiwara no Teika, and the date of about 1235 is the traditional one, while the early history of the selection is uncertain. The anthology is still the basis of the card game karuta, which is played with the same hundred poems.",
    ),

)
