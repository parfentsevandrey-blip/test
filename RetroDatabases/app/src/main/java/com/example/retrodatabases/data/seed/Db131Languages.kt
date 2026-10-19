package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 730 to 740: John of Damascus's treatises on the images about 730, the Izumo fudoki of 733 and Bede's history of 731. */
val db131Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 131,
        kind = Kind.LANGUAGE,
        section = "Greek",
        title = "John of Damascus's three treatises on the divine images, written against the iconoclasts about 730",
        originalTitle = "Apologetikoi logoi pros tous diaballontas tas hagias eikonas",
        author = "John of Damascus",
        year = 730,
        summary = "John of Damascus's three treatises on the divine images, written in Greek against the iconoclasts, are usually dated to about 730.",
        details = "He wrote them in the Umayyad lands, outside the reach of the Byzantine emperor, in answer to Leo III's attack on the icons. Accounts date the treatises between 726 and 730, and they became the chief Greek defence of the images in the iconoclast quarrel.",
        rare = true,
    ),

    entry(
        databaseId = 131,
        kind = Kind.LANGUAGE,
        section = "Japanese",
        title = "The Izumo fudoki, the gazetteer of Izumo Province presented to the court in 733",
        originalTitle = "Izumo no kuni fudoki",
        author = "Izumo provincial officials",
        year = 733,
        summary = "The Izumo fudoki, a gazetteer of Izumo Province written in Classical Chinese, was presented to the court in 733.",
        details = "In 713 the court ordered each province to record its districts, products, land, hills and rivers and old stories, and the Izumo text is the only one of these gazetteers to survive complete. It keeps local myths and place names that the national chronicles do not give.",
        rare = true,
    ),

    entry(
        databaseId = 131,
        kind = Kind.LANGUAGE,
        section = "Latin",
        title = "Bede's Ecclesiastical History of the English People, a Latin history completed in 731",
        originalTitle = "Historia ecclesiastica gentis Anglorum",
        author = "Bede",
        year = 731,
        summary = "Bede's Ecclesiastical History of the English People, a Latin history of the English church and kingdoms, was completed in 731.",
        details = "The history runs in five books, from Caesar's landing in Britain to Bede's own day, and it reports the synod of Whitby of 664. Bede died in 735, and the work is the chief source for the early English church.",
    ),

)
