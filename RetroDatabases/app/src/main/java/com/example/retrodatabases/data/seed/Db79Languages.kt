package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 1250 to 1260: a Persian garden of stories, a Norwegian king's mirror, a Latin report from Karakorum, a Flemish fox, a Persian history of the Mongols and an English monk's chronicle. */
val db79Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 79,
        kind = Kind.LANGUAGE,
        section = "Persian",
        title = "Saadi's Gulistan, a Persian book of stories and verse",
        originalTitle = "Gulistan",
        author = "Saadi of Shiraz",
        year = 1258,
        summary = "Saadi completed the Gulistan at Shiraz in 1258, a Persian book that mixes prose stories with verse and was widely copied and read.",
        details = "The Gulistan, or Rose Garden, is divided into eight chapters on the manners of kings, dervishes, contentment, love and conversation. The year 1258 is the usual date for its completion, and the author had written his Bustan, a verse work, a year earlier.",
        rare = true,
    ),

    entry(
        databaseId = 79,
        kind = Kind.LANGUAGE,
        section = "Old Norse",
        title = "The King's Mirror, a Norwegian instruction book in Old Norse",
        originalTitle = "Konungs skuggsja",
        author = "An unknown Norwegian author",
        year = 1250,
        summary = "The King's Mirror is a Norwegian instruction book in Old Norse prose, written about 1250 as a dialogue between a father and his son.",
        details = "It describes the royal court, the life of merchants, and the lands and seas of the North, including Greenland and Iceland. Its date is given as about 1250, and accounts give a range from about 1250 to 1263.",
        rare = true,
    ),

    entry(
        databaseId = 79,
        kind = Kind.LANGUAGE,
        section = "Latin travel",
        title = "William of Rubruck's report of his journey to the Mongol court",
        originalTitle = "Itinerarium fratris Willelmi de Rubruquis",
        author = "William of Rubruck",
        year = 1255,
        summary = "William of Rubruck wrote his Latin report of the Mongol court in 1255, after a journey to Karakorum that began in 1253.",
        details = "The Franciscan friar was sent to the court of Mongke Khan by King Louis IX of France, and his report describes the silver tree and the religious life of the court. The year 1255 is the usual date for the report, and his stay at Karakorum is given as 1254.",
    ),

    entry(
        databaseId = 79,
        kind = Kind.LANGUAGE,
        section = "Middle Dutch",
        title = "Reynard the Fox in Middle Dutch verse, a Flemish satire",
        originalTitle = "Van den vos Reynaerde",
        author = "Willem",
        year = 1250,
        summary = "Van den vos Reynaerde is a Middle Dutch verse tale of Reynard the Fox that dates from about 1250 and satirises the court and the clergy.",
        details = "The poet names himself as Willem in the closing lines, and the poem adapts the French tales of Renart for a Flemish audience. The date of about 1250 is the usual estimate, and the poem is one of the major early works of Middle Dutch literature.",
    ),

    entry(
        databaseId = 79,
        kind = Kind.LANGUAGE,
        section = "Persian history",
        title = "Juvaini's history of the Mongol world conquest, written in Persian",
        originalTitle = "Tarikh-i Jahangushay",
        author = "Ata-Malik Juvaini",
        year = 1260,
        summary = "Juvaini completed his Persian history of the Mongol conquests in 1260, and it is one of the main written sources for the Mongol rulers of Iran.",
        details = "The history covers Chinggis Khan and the rulers after him, and its author served the Mongol governors and the court in Iran. The completion date of 1260 is given in modern scholarship, and the work survives in several manuscripts and editions.",
        rare = true,
    ),

    entry(
        databaseId = 79,
        kind = Kind.LANGUAGE,
        section = "Latin chronicle",
        title = "Matthew Paris's Chronica majora, an English chronicle in Latin",
        originalTitle = "Chronica majora",
        author = "Matthew Paris",
        year = 1259,
        summary = "Matthew Paris wrote his Latin Chronica majora at the abbey of St Albans, and its last entries date from 1259, the year of his death.",
        details = "The entries of the 1250s are his own, and the chronicle is illustrated with drawings of the kind that few other English chronicles carry. Several manuscripts of it survive, and the year 1259 is the usual date given for the end of his work.",
        rare = true,
    ),

)
