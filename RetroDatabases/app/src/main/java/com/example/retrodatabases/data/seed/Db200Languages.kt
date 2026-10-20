package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 40 to 50: Pomponius Mela's Chorographia, the earliest surviving Latin geography of the world. */
val db200Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 200,
        kind = Kind.LANGUAGE,
        section = "Latin",
        title = "Pomponius Mela's Chorographia, the earliest surviving Latin geography of the world",
        originalTitle = "De chorographia",
        author = "Pomponius Mela",
        year = 43,
        summary = "Pomponius Mela's Chorographia describes the whole known world in three books, and it is the earliest surviving geography of the world written in Latin.",
        details = "Mela sets out the coasts, peoples and islands of the world in a fixed order, so the book reads as a list of places rather than a story. The date of about 43 is inferred from an apparent allusion to Claudius's British triumph, and older scholarship placed the work under Caligula, while the titles De situ orbis and De chorographia libri tres name the same book.",
        rare = true,
    ),

)
