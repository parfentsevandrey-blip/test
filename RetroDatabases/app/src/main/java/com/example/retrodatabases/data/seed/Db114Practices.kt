package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One practice of 910: the charter of Cluny, which founded the abbey with its monks free to elect their own abbot. */
val db114Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 114,
        kind = Kind.PRACTICE,
        section = "Burgundy",
        title = "The charter of Cluny, which left the monks free to elect their own abbot",
        originalTitle = "Charter of Cluny",
        author = "William I, duke of Aquitaine",
        year = 910,
        summary = "The charter of Cluny, issued in 910 by William I of Aquitaine, founded the abbey free of local lords and let its monks elect their own abbot.",
        details = "The charter is usually dated 11 September 910, and it placed the abbey under the protection of the papacy. Its terms became a model for later reform houses, which often copied the freedom from lay control that the charter set out.",
    ),

)
