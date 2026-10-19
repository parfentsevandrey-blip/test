package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 890 to 900: Asser's Life of King Alfred, the Anglo-Saxon Chronicle, the Ruiju kokushi, Abbo's siege poem and al-Baladhuri's Futuh al-buldan. */
val db115Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 115,
        kind = Kind.LANGUAGE,
        section = "Latin",
        title = "Asser's Life of King Alfred, a Latin biography of the West Saxon king",
        originalTitle = "Vita Alfredi regis",
        author = "Asser, a monk from St Davids who later became bishop of Sherborne",
        year = 893,
        summary = "Asser's Life of King Alfred, a Latin biography of the West Saxon king, is dated 893 in the usual account.",
        details = "The Life covers Alfred's wars against the Vikings, his learning and his foundations. Its date and authorship are questioned, since some scholars argue that the text was written later than 893 while others defend the traditional account.",
    ),

    entry(
        databaseId = 115,
        kind = Kind.LANGUAGE,
        section = "English",
        title = "The Anglo-Saxon Chronicle, begun in King Alfred's circle",
        originalTitle = "Anglo-Saxon Chronicle",
        author = "Anonymous monks of several English houses",
        year = 890,
        summary = "The Anglo-Saxon Chronicle, a year-by-year history of England, was begun in King Alfred's circle about 890.",
        details = "The early annals were gathered in Wessex under Alfred, and the record was then kept up in several monasteries, so the copies differ from one another. One copy runs on to 1154, and about 890 is the usual date for the start.",
    ),

    entry(
        databaseId = 115,
        kind = Kind.LANGUAGE,
        section = "Japanese",
        title = "The Ruiju kokushi, a Japanese collection of the national histories arranged by topic",
        originalTitle = "Ruiju kokushi",
        author = "Sugawara no Michizane, usually named as compiler",
        year = 892,
        summary = "The Ruiju kokushi, a Japanese collection that sorts the national histories by topic, was compiled in 892.",
        details = "It takes the entries of Japan's six national histories and regroups them under subject headings instead of by year. Much of it has been lost, and 892 is the year usually given for its compilation.",
        rare = true,
    ),

    entry(
        databaseId = 115,
        kind = Kind.LANGUAGE,
        section = "Latin",
        title = "Abbo of Saint-Germain's Latin poem on the Viking siege of Paris",
        originalTitle = "Bella Parisiacae urbis",
        author = "Abbo, a monk of Saint-Germain-des-Pres",
        year = 897,
        summary = "Abbo's Latin poem on the Viking siege of Paris, which began in 885, was finished about 897 by a monk who was inside the city.",
        details = "The poem covers the siege and the fighting around Paris that followed it. Abbo was inside the city during the siege and wrote in verse afterwards, and about 897 is the date given for the completed poem.",
        rare = true,
    ),

    entry(
        databaseId = 115,
        kind = Kind.LANGUAGE,
        section = "Arabic",
        title = "Al-Baladhuri's Futuh al-buldan, an Arabic history of the early Muslim conquests",
        originalTitle = "Futuh al-buldan",
        author = "Ahmad ibn Yahya al-Baladhuri",
        year = 892,
        summary = "Al-Baladhuri's Conquests of the Lands, an Arabic history of the early Muslim conquests, is dated here to 892, the year its author died.",
        details = "This is a placed record: the first form of the book is not closely dated, so 892, the year of al-Baladhuri's death, is the placed year and not the date of the writing. The book covers the conquests of Syria, Iraq, Iran and Egypt, and it records the terms of surrender that towns were given.",
        rare = true,
    ),

)
