package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 370 to 380: a Latin poem on the Moselle, Gregory of Nazianzus's Theological Orations and Epiphanius's Panarion. */
val db167Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 167,
        kind = Kind.LANGUAGE,
        section = "Latin",
        title = "Ausonius's Mosella, a Latin poem on the river Moselle",
        originalTitle = "Mosella",
        author = "Decimus Magnus Ausonius",
        year = 370,
        summary = "Ausonius's Mosella is a Latin poem of some 480 lines describing the river Moselle, its vineyards and its fishermen.",
        details = "Scholars usually date the poem to about 370, and a review of a recent edition gives 375 as the latest possible date, so accounts differ by a few years. It was written while the imperial court was at Trier on the Moselle, where Ausonius had been tutor to the young Gratian.",
    ),

    entry(
        databaseId = 167,
        kind = Kind.LANGUAGE,
        section = "Greek",
        title = "Gregory of Nazianzus's Theological Orations, five sermons on the Trinity",
        originalTitle = "Logoi theologikoi",
        author = "Gregory of Nazianzus",
        year = 379,
        summary = "Gregory of Nazianzus preached five Theological Orations at Constantinople in 379 and 380 to defend the Nicene faith about the Trinity.",
        details = "He preached them in a small church he called the Anastasia, and the series is usually dated to the years 379 and 380. The orations were later gathered with his other works, and copies of them survive in many Greek manuscripts.",
        rare = true,
    ),

    entry(
        databaseId = 167,
        kind = Kind.LANGUAGE,
        section = "Greek",
        title = "Epiphanius's Panarion, a medicine chest against eighty heresies",
        originalTitle = "Panarion",
        author = "Epiphanius of Salamis",
        year = 377,
        summary = "Epiphanius of Salamis finished the Panarion in about 377, a Greek catalogue of eighty heresies, each treated with a refutation as its remedy.",
        details = "He wrote it in Cyprus between 374 and 377, and the title means a medicine chest because every heresy is treated like an illness with its own cure. Later writers against heresy used its accounts of named sects.",
        rare = true,
    ),

)
