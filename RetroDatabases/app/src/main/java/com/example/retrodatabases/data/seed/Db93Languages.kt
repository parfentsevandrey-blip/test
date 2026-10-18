package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 1110 to 1120: the large Jurchen script, a Pisan verse history, a Kievan chronicle, two Latin histories and an English law book. */
val db93Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 93,
        kind = Kind.LANGUAGE,
        section = "Writing",
        title = "The large Jurchen script, devised for the Jin state from Chinese and Khitan signs",
        originalTitle = "Nuzhen da zi",
        author = "Wanyan Xiyin, working under Aguda",
        year = 1119,
        summary = "The large Jurchen script was devised for the Jurchen language of the Jin state, and a Jin history dates its completion to 1119.",
        details = "Its signs were built from Chinese characters and the Khitan writing system, and it was made at the command of Aguda, the first Jin emperor. A Jin history places its completion in the eighth month of the third Tianfu year, which is 1119, while other accounts give 1120. Modern descriptions also differ on whether the signs are ideographic or logographic and phonetic.",
        rare = true,
    ),

    entry(
        databaseId = 93,
        kind = Kind.LANGUAGE,
        section = "Latin",
        title = "The Liber Maiolichinus, a Latin verse history of Pisa's Balearic expedition",
        originalTitle = "Liber Maiolichinus de gestis Pisanorum illustribus",
        author = "An unknown Pisan writer",
        year = 1117,
        summary = "The Liber Maiolichinus is a Latin verse history of Pisa's expedition against the Balearic islands, written by an unknown Pisan about 1117.",
        details = "The poem was written after the expedition of 1113 to 1115 and sets the Pisan share of the campaign in verse. The date of about 1117 is an estimate, so the year here is a rounded one within the range of the disputes over the poem's dating.",
        rare = true,
    ),

    entry(
        databaseId = 93,
        kind = Kind.LANGUAGE,
        section = "Old East Slavic",
        title = "The Primary Chronicle of Kiev, the Tale of Bygone Years",
        originalTitle = "Povest' vremennykh let",
        author = "Nestor by tradition, and later editors",
        year = 1113,
        summary = "The Primary Chronicle of Kievan Rus was compiled at Kiev about 1113, and tradition credits its first version to the monk Nestor.",
        details = "It is the oldest surviving history of the East Slavs, traditionally tied to the Kiev Caves monastery, and it runs from the legendary past to the early twelfth century. Its oldest surviving copy is the Laurentian Codex of 1377, and a later revision by the abbot Sylvester is usually dated 1116, though some accounts give 1118.",
    ),

    entry(
        databaseId = 93,
        kind = Kind.LANGUAGE,
        section = "Latin",
        title = "Guibert of Nogent's account of his own life, the De vita sua",
        originalTitle = "De vita sua, sive Monodiae",
        author = "Guibert of Nogent",
        year = 1115,
        summary = "Guibert of Nogent, an abbot near Laon, wrote an account of his own life in Latin about 1115, one of the earliest autobiographies of the Middle Ages.",
        details = "The book is also called the Monodiae, and it describes his childhood, his schooling and his later quarrels as abbot. The date of about 1115 is the usual estimate for its writing, and Guibert died about 1124.",
        rare = true,
    ),

    entry(
        databaseId = 93,
        kind = Kind.LANGUAGE,
        section = "Latin",
        title = "Orderic Vitalis's Ecclesiastical History, begun in Normandy in 1114",
        originalTitle = "Historia ecclesiastica",
        author = "Orderic Vitalis",
        year = 1114,
        summary = "Orderic Vitalis began his Ecclesiastical History in 1114 at the abbey of Saint-Evroul in Normandy, and he kept writing it until about 1141.",
        details = "Orderic was born in England in 1075 and was sent as a boy to the Norman abbey, so his history mixes English, Norman and church affairs. It runs from the Incarnation to about 1141, and the opening date of 1114 is the one most often given for its start.",
    ),

    entry(
        databaseId = 93,
        kind = Kind.LANGUAGE,
        section = "Law",
        title = "The Leges Henrici Primi, an anonymous Latin law book of England",
        originalTitle = "Leges Henrici Primi",
        author = "An anonymous compiler",
        year = 1115,
        summary = "The Leges Henrici Primi is an anonymous Latin collection of English and Norman law, put together in the years around 1115 to 1118.",
        details = "Despite its title it is a private compilation, not a royal statute, and it draws on Anglo-Saxon law codes and on the customs of the Anglo-Norman court. The span of about 1115 to 1118 is the usual estimate, and the book survives in several later copies.",
        rare = true,
    ),

)
