package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 190 to 200: the Muratorian fragment, Tertullian's Apology and Ying Shao's Fengsu tongyi. */
val db185Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 185,
        kind = Kind.LANGUAGE,
        section = "Latin",
        title = "The Muratorian fragment, a Latin list of the New Testament books",
        originalTitle = "Fragmentum Muratorianum",
        author = "No known author",
        year = 190,
        summary = "The Muratorian fragment is a damaged Latin list of the New Testament books, one of the earliest surviving lists of the Christian scriptures, and this record uses the round date 190 for it.",
        details = "Ludovico Antonio Muratori published the text in 1740 from a manuscript in Milan's Ambrosian Library, copied about the eighth century, and the opening of the list is lost. The surviving lines name the Gospels of Luke and John, the Acts, the letters of Paul, Jude and two letters of John, and they leave out Hebrews, James and the letters of Peter. Scholars presume a Greek original and date its composition from about 170 to about 200, so 190 is a round date within that range, and the text rejects writings linked with Marcion and with Gnostic teachers.",
        rare = true,
    ),

    entry(
        databaseId = 185,
        kind = Kind.LANGUAGE,
        section = "Latin",
        title = "Tertullian's Apology, a Latin defence of Christians written at Carthage",
        originalTitle = "Apologeticum",
        author = "Tertullian",
        year = 197,
        summary = "Tertullian's Apologeticum is a Latin defence of Christians written at Carthage about 197, and it is among the earliest surviving Latin apologies for the faith.",
        details = "The work answers the charges of atheism, disloyalty to the emperor and secret crimes brought against Christians, and it argues that no one should be forced into a religion. It was written under Septimius Severus, and 197 is the usual date, although some older scholarship argued against any date before that year.",
        rare = true,
    ),

    entry(
        databaseId = 185,
        kind = Kind.LANGUAGE,
        section = "Chinese",
        title = "Ying Shao's Fengsu tongyi, a late Han work on customs",
        originalTitle = "Fengsu tongyi",
        author = "Ying Shao",
        year = 195,
        summary = "Ying Shao's Fengsu tongyi is a Chinese work on the customs and institutions of the late Eastern Han, dated about 195.",
        details = "Only ten of its original thirty chapters survive, and other passages are known from fragments collected in later anthologies, so the text is incomplete. The title is usually rendered as Comprehensive Meanings of Customs, and the sources give 195 only as an approximate date, with no year of completion.",
        rare = true,
    ),

)
