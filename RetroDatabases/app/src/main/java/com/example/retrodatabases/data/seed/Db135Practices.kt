package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 690 to 700: the first palace examination under Empress Wu in 690, and the St Cuthbert Gospel, whose binding is dated to the late seventh or early eighth century and placed at 698. */
val db135Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 135,
        kind = Kind.PRACTICE,
        section = "Chinese",
        title = "The palace examination, first held in 690 under Empress Wu, set by the ruler herself in the palace",
        originalTitle = "dianshi",
        author = "Empress Wu Zetian",
        year = 690,
        summary = "Empress Wu held a palace examination in 690, a test set by the ruler in person and held in the palace.",
        details = "The test let the ruler choose officials directly, and accounts credit the first palace examination to Empress Wu's Zhou dynasty, which began in 690. The palace examination later became a regular final stage of the Chinese civil service examinations.",
        rare = true,
    ),

    entry(
        databaseId = 135,
        kind = Kind.PRACTICE,
        section = "English",
        title = "The St Cuthbert Gospel, a small Latin copy of John's Gospel with the oldest surviving intact European binding",
        originalTitle = "Evangelium secundum Iohannem",
        author = "Northumbrian scribes and binders",
        year = 698,
        summary = "The St Cuthbert Gospel is a small Latin copy of John's Gospel in a binding credited as the oldest surviving intact European book binding.",
        details = "The sources disagree on the date of the binding, which they place between the late seventh and the early eighth century. The year 698, when Cuthbert's body was moved into a new coffin at Lindisfarne, is the anchor used here and not the date of the binding.",
    ),

)
