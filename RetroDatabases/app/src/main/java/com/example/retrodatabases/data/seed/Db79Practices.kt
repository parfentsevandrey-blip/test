package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 1250 to 1260: the writs of 1254 that summoned two knights from each English shire to Westminster. */
val db79Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 79,
        kind = Kind.PRACTICE,
        section = "Parliament",
        title = "Two knights from each shire summoned to Westminster by writ of 1254",
        originalTitle = "Writs for two knights of each shire",
        author = "Henry III and his sheriffs",
        year = 1254,
        summary = "In 1254 Henry III ordered the sheriffs to have two knights from each English shire come to Westminster, a practice that later became the election of county members of Parliament.",
        details = "The knights were to come with authority to agree a grant of money for the king, and the sheriffs had them chosen in the county court. The year 1254 is the usual date for the first writs of this kind, but accounts differ on whether a summons of knights in 1213 belongs to the same practice, so its start is disputed.",
    ),

)
