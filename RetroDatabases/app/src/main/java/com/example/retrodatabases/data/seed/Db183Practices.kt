package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 210 to 220: military diplomas, bronze certificates for discharged auxiliaries, placed at 212 because no auxiliary diploma is dated to the decade. */
val db183Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 183,
        kind = Kind.PRACTICE,
        section = "Service",
        title = "Military diplomas, bronze certificates of citizenship for discharged auxiliary soldiers",
        originalTitle = "Diplomata militaria",
        author = "Roman army administration",
        year = 212,
        summary = "A military diploma was a two-leaved bronze certificate that gave citizenship and the right to a legal marriage to a discharged auxiliary soldier and his children.",
        details = "Auxiliary diplomas appear to stop about 203, so the sources do not date an auxiliary diploma to the decade. The year 212 is the year of the anchor, the Constitutio Antoniniana, not the date of any diploma.",
        rare = true,
    ),

)
