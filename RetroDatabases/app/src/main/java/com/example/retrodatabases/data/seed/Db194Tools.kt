package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 100 to 110: the Vindolanda tablets, thin wooden leaves written in ink by the garrison of a fort on the northern frontier of Roman Britain. */
val db194Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 194,
        kind = Kind.TOOL,
        section = "Writing",
        title = "The Vindolanda tablets, ink letters and accounts written on wood",
        originalTitle = "Vindolanda tablets",
        author = "The garrison of the fort at Vindolanda",
        year = 100,
        summary = "The Vindolanda tablets are thin leaves of wood on which the garrison of a Roman fort on the northern frontier wrote letters and accounts in carbon-based ink.",
        details = "The birthday invitation to Sulpicia Lepidina, asking her to attend Claudia Severa's birthday on 11 September, is dated about 97 to 103 in one account and about 100 in another. The surviving texts include military reports, accounts, letters and supply lists, and the ink is carbon-based on both faces of the wood.",
        rare = true,
    ),

)
