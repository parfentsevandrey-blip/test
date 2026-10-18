package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 1250 to 1260, only two verified: a route map from London to Apulia drawn about 1250 and Vincent of Beauvais's encyclopedia of about 1260. */
val db79Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 79,
        kind = Kind.TOOL,
        section = "Maps",
        title = "Matthew Paris's route map from London to Apulia",
        originalTitle = "Itinerary of Matthew Paris",
        author = "Matthew Paris",
        year = 1250,
        summary = "Matthew Paris drew a route map of the road from London to Apulia about 1250, a diagram of stopping places for travellers.",
        details = "The map lists the towns on the road in order, and it survives in manuscripts of his chronicle. The date of about 1250 is the usual estimate for the drawing, which was made in the years of his greatest work.",
    ),

    entry(
        databaseId = 79,
        kind = Kind.TOOL,
        section = "Encyclopedias",
        title = "Vincent of Beauvais's Speculum majus, a Latin encyclopedia",
        originalTitle = "Speculum maius",
        author = "Vincent of Beauvais",
        year = 1260,
        summary = "Vincent of Beauvais's Speculum maius is a Latin encyclopedia of nature, doctrine and history, completed in its main form about 1260.",
        details = "The work was composed over many years in the 1240s and 1250s, and accounts give different dates for its completion. It gathers extracts from earlier writers under the headings of nature, doctrine and history, and it became one of the most used reference books of the Middle Ages.",
        rare = true,
    ),

)
