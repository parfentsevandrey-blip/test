package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 380 to 390: the station services of Jerusalem's churches, as the pilgrim Egeria describes them. */
val db166Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 166,
        kind = Kind.PRACTICE,
        section = "Liturgy",
        title = "The daily station services of Jerusalem's churches, as the pilgrim Egeria describes them",
        originalTitle = "Stationes",
        author = "Egeria and the church of Jerusalem",
        year = 384,
        summary = "Egeria's account shows that Jerusalem's churches held a round of station services through the day and the week in the late fourth century.",
        details = "Egeria, a pilgrim probably from Spain, describes the services as stations, with processions that moved from church to church on set days and a full order of readings and prayers. Her Holy Week account includes a Palm Sunday procession and a Maundy Thursday eucharist, and her text is usually dated to about 381 to 384. Other accounts widen the window to 363 to 540 or put the writing after 394, and the identity of the author is probable rather than proven, since the only manuscript is an incomplete eleventh-century copy that does not name its author.",
        rare = true,
    ),

)
