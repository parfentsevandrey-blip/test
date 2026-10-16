package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 1300 to 1310: Dante's Latin treatise on the mother tongue, a German poem, a Greek anthology, an Anglo-French siege poem, an Icelandic manuscript and a French history of the East. */
val db74Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 74,
        kind = Kind.LANGUAGE,
        section = "Latin",
        title = "Dante's De vulgari eloquentia, a Latin treatise on the mother tongue",
        originalTitle = "De vulgari eloquentia",
        author = "Dante Alighieri",
        year = 1305,
        summary = "Dante's Latin treatise on the mother tongue asks which Italian speech suits high poetry, and it breaks off in the middle of its second book.",
        details = "The work is usually dated to about 1302 to 1305. It compares the Italian dialects and their fitness for poetry before it stops, so the planned later books are missing.",
    ),

    entry(
        databaseId = 74,
        kind = Kind.LANGUAGE,
        section = "German",
        title = "Hugo von Trimberg's Der Renner, a German poem of advice and manners",
        originalTitle = "Der Renner",
        author = "Hugo von Trimberg",
        year = 1300,
        summary = "Hugo von Trimberg's Der Renner is a long German poem of advice for every rank of society, usually dated to about 1300.",
        details = "The poem is written in German rhyming verse and takes its readers through the faults and duties of each estate, from princes to peasants. Its many surviving manuscripts show that it was copied widely in the later Middle Ages.",
        rare = true,
    ),

    entry(
        databaseId = 74,
        kind = Kind.LANGUAGE,
        section = "Greek",
        title = "The Planudean Anthology of Greek epigrams, gathered at Constantinople",
        originalTitle = "Anthologia Planudea",
        author = "Maximus Planudes",
        year = 1301,
        summary = "Maximus Planudes gathered a large collection of short Greek poems called epigrams at Constantinople in 1301, using older anthologies as his sources.",
        details = "Planudes rearranged the older material, so the order of his collection is his own. The date 1301 is usually given for the compilation, which is known from manuscripts copied in the centuries after it.",
    ),

    entry(
        databaseId = 74,
        kind = Kind.LANGUAGE,
        section = "Anglo-French",
        title = "The Roll of Caerlaverock, an Anglo-French poem of a siege",
        originalTitle = "Le Siege de Carlaverock",
        author = "An anonymous herald of Edward I",
        year = 1300,
        summary = "An anonymous Anglo-French poem describes Edward I's siege of Caerlaverock Castle in Scotland in July 1300 and names the knights who took part.",
        details = "The writer was probably a herald in the royal army, and the poem lists the knights with their coats of arms, so it doubles as a roll of the nobles present. The siege is dated to July 1300, and the poem was written soon after it.",
        rare = true,
    ),

    entry(
        databaseId = 74,
        kind = Kind.LANGUAGE,
        section = "Old Norse",
        title = "Hauksbok, an Icelandic manuscript of sagas and learning",
        originalTitle = "Hauksbok",
        author = "Haukr Erlendsson",
        year = 1302,
        summary = "Hauksbok is an Icelandic manuscript of sagas, a book of settlements and learned texts, compiled about 1302 to 1310 by Haukr Erlendsson.",
        details = "The compiler was an Icelandic lawman, and the manuscript gathers in one book texts that had circulated separately, including a version of the Book of Settlements. The dates are usually given as about 1302 to 1310, so 1302 marks the start of the work.",
        rare = true,
    ),

    entry(
        databaseId = 74,
        kind = Kind.LANGUAGE,
        section = "French",
        title = "Hayton's Flower of the Histories of the Orient, on the Mongol rulers",
        originalTitle = "La Flor des estoires de la Terre d'Orient",
        author = "Hayton of Corycus",
        year = 1307,
        summary = "Hayton of Corycus had his history of the East written in French in 1307, a guide to the Mongols and the other peoples of the East.",
        details = "The book describes the Mongol rulers and the routes of the East, and the year 1307 is the usual date for the text.",
    ),

)
