package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 1120 to 1130: a Latin guide to reading, a Latin history of England, the Peterborough annals, a French verse voyage, an Icelandic history and a Chinese account of Koryo. */
val db92Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 92,
        kind = Kind.LANGUAGE,
        section = "Latin",
        title = "Hugh of St Victor's Didascalicon, a guide to the arts and to reading",
        originalTitle = "Didascalicon de studio legendi",
        author = "Hugh of St Victor",
        year = 1125,
        summary = "Hugh of St Victor's Didascalicon is a Latin guide to the arts of learning and to the order in which a student should read them.",
        details = "Hugh was a teacher at the school of St Victor in Paris. The date is disputed: one account puts the work before 1125, and another places it between 1125 and 1130, so 1125 is used here only as a working year.",
    ),

    entry(
        databaseId = 92,
        kind = Kind.LANGUAGE,
        section = "Latin",
        title = "William of Malmesbury's Deeds of the English Kings, first finished in 1125",
        originalTitle = "Gesta regum Anglorum",
        author = "William of Malmesbury",
        year = 1125,
        summary = "William of Malmesbury finished the first version of his Deeds of the English Kings in 1125, a Latin history of England from the Anglo-Saxon kings to his own day.",
        details = "The work was later revised and extended, so 1125 dates only the first version. It survives in many medieval copies and is one of the main Latin histories of England written in the twelfth century.",
    ),

    entry(
        databaseId = 92,
        kind = Kind.LANGUAGE,
        section = "English",
        title = "The Peterborough Chronicle, its English annals from 1121",
        originalTitle = "Anglo-Saxon Chronicle, manuscript E",
        author = "Monks of Peterborough Abbey",
        year = 1121,
        summary = "The Peterborough Chronicle, manuscript E of the Anglo-Saxon Chronicle, keeps annals in English prose from the 1120s down to 1154.",
        details = "The annals up to 1121 were copied from a lost model, the First Continuation runs to 1131, and a Second Continuation by another writer covers 1132 to 1154. Accounts differ on whether the last annal is still Old English or already early Middle English.",
    ),

    entry(
        databaseId = 92,
        kind = Kind.LANGUAGE,
        section = "French",
        title = "Benedeit's Voyage of St Brendan, a French verse voyage of about 1121",
        originalTitle = "Le Voyage de Saint Brandan",
        author = "Benedeit",
        year = 1121,
        summary = "Benedeit's Voyage of St Brendan retells the legend of an Irish monk's sea voyage in French verse, written about 1121.",
        details = "The poem follows the monk's voyage among islands and marvels at sea. Its date is approximate, and the poem is usually dated to about 1121.",
        rare = true,
    ),

    entry(
        databaseId = 92,
        kind = Kind.LANGUAGE,
        section = "Old Norse",
        title = "Ari the Learned's Islendingabok, a history of Iceland in Icelandic",
        originalTitle = "Islendingabok",
        author = "Ari Thorgilsson the Learned",
        year = 1122,
        summary = "Ari the Learned wrote the Islendingabok in Icelandic in the early 1120s, a short history of Iceland from its first settlement.",
        details = "Accounts date the book between about 1122 and 1133, and 1122 is the start of that range. It was written in the vernacular rather than Latin, which makes it one of the earliest histories in Icelandic.",
    ),

    entry(
        databaseId = 92,
        kind = Kind.LANGUAGE,
        section = "Chinese",
        title = "Xu Jing's Illustrated Account of the Koryo mission, finished in 1124",
        originalTitle = "Xuanhe fengshi Gaoli tujing",
        author = "Xu Jing",
        year = 1124,
        summary = "Xu Jing finished his illustrated account of a Chinese embassy to Koryo in 1124, a book based on his own journey there in 1123.",
        details = "He travelled with the Chinese mission to the Goryeo court in 1123, and the book describes the kingdom and its people. A review of the book argues that Xu's political setting and possible bias should be weighed when the account is used.",
        rare = true,
    ),

)
