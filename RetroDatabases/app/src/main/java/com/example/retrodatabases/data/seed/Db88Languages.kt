package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 1160 to 1170: three Old French verse works of about 1160, a Hebrew chronicle of tradition and Maimonides's Arabic commentary on the Mishnah. */
val db88Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 88,
        kind = Kind.LANGUAGE,
        section = "Old French",
        title = "Benoit de Sainte-Maure's Roman de Troie, a French romance of the Trojan War",
        originalTitle = "Roman de Troie",
        author = "Benoit de Sainte-Maure",
        year = 1160,
        summary = "Benoit de Sainte-Maure wrote the Roman de Troie in octosyllabic couplets about 1160 to 1165, a long French romance of the Trojan War.",
        details = "The poem was written for the Anglo-Norman court, and it became a source for later medieval versions of the Troy story. The dates for its composition are approximate, and accounts give different years within the span.",
    ),

    entry(
        databaseId = 88,
        kind = Kind.LANGUAGE,
        section = "Anglo-Norman",
        title = "Wace's Roman de Rou, a verse history of the dukes of Normandy",
        originalTitle = "Roman de Rou",
        author = "Wace",
        year = 1160,
        summary = "Wace began his verse history of the dukes of Normandy about 1160, at the request of Henry II, and left it unfinished.",
        details = "The poem is written in octosyllabic couplets and takes the Norman dukes from Rollo onward. Wace had already finished his Roman de Brut in 1155, and the Rou stops before its planned end, so it survives as an unfinished work.",
    ),

    entry(
        databaseId = 88,
        kind = Kind.LANGUAGE,
        section = "Hebrew",
        title = "Abraham ibn Daud's Book of Tradition, a Hebrew chronicle of the sages",
        originalTitle = "Sefer ha-Qabbalah",
        author = "Abraham ibn Daud",
        year = 1161,
        summary = "Abraham ibn Daud completed his Sefer ha-Qabbalah, a Hebrew account of the chain of rabbinic tradition, at Toledo in 1161.",
        details = "The book traces the transmission of the tradition from the earlier sages to the author's own time. Its later sections give an early account of how Aristotle's philosophy reached Jewish readers, which makes it one of the first histories of philosophy written in Hebrew.",
        rare = true,
    ),

    entry(
        databaseId = 88,
        kind = Kind.LANGUAGE,
        section = "Arabic",
        title = "Maimonides's Commentary on the Mishnah, written in Arabic for readers of the oral law",
        originalTitle = "Kitab al-Siraj",
        author = "Moses Maimonides",
        year = 1168,
        summary = "Maimonides finished his Arabic commentary on the Mishnah in 1168, a work that explains each mishnah of the oral law in turn.",
        details = "He began the commentary in North Africa, and accounts differ on the starting date, which is given as the late 1150s. The finished work is usually dated to 1168, and it was written in Arabic rather than in Hebrew.",
        rare = true,
    ),

    entry(
        databaseId = 88,
        kind = Kind.LANGUAGE,
        section = "Old French",
        title = "The Roman d'Eneas, an anonymous Old French Aeneid",
        originalTitle = "Roman d'Eneas",
        author = "Anonymous",
        year = 1160,
        summary = "The Roman d'Eneas is an anonymous Old French retelling of Virgil's Aeneid in octosyllabic couplets, written about 1160.",
        details = "The poem turns Virgil's story of Aeneas into romance, and it gives a full place to the love of Dido. The author is unknown, and the date about 1160 is the one usually given.",
        rare = true,
    ),

)
