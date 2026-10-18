package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 1132: the Cistercian lay brothers who worked the granges. */
val db91Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 91,
        kind = Kind.PRACTICE,
        section = "Monasteries",
        title = "Lay brothers who farmed the Cistercian granges in the 1130s",
        originalTitle = "Conversi",
        author = "The Cistercian order",
        year = 1132,
        summary = "Cistercian lay brothers, called conversi, worked the outlying farms known as granges, and English houses such as Rievaulx, founded in 1132, were among the early Cistercian foundations.",
        details = "The lay brothers took vows but kept fewer of the choir offices than the monks, which left them free for work in the fields and on the granges. The practice spread with the order's houses in the decades that followed.",
    ),

)
