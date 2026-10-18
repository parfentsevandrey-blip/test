package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 1059: a signed profession of faith on the Eucharist, required of Berengar of Tours at a Roman synod. */
val db99Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 99,
        kind = Kind.PRACTICE,
        section = "Church",
        title = "The profession of faith that Berengar of Tours signed at the Roman synod of 1059",
        originalTitle = "Professio fidei",
        author = "Berengar of Tours and the Roman synod",
        year = 1059,
        summary = "At a Roman synod in 1059 Berengar of Tours was made to sign a set written profession of faith on the Eucharist, a fixed wording that he had to accept.",
        details = "The wording stressed that the consecrated bread and wine are handled and broken physically by the hands of the priests. Historians differ on how far Berengar accepted it as his own belief.",
        rare = true,
    ),

)
