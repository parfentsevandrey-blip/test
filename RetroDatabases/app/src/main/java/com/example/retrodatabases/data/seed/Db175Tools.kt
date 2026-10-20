package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 290 to 300: the Antonine Itinerary, a list of Roman road stations and distances, placed at 297 because its date is disputed. */
val db175Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 175,
        kind = Kind.TOOL,
        section = "Roads",
        title = "The Antonine Itinerary, a list of Roman road stations and distances",
        originalTitle = "Itinerarium Antonini Augusti",
        author = "Unknown compiler",
        year = 297,
        summary = "The Antonine Itinerary lists Roman road stations and the distances between them, route by route, and it is placed here at 297.",
        details = "The surviving list is dated by scholars to the late third or early fourth century, although an earlier original of the Caracalla era is also proposed, so no single date is firm. The sources do not date this tool; 297 is the year of the anchor, the census, not the year of the list, and the earliest surviving copies are of the seventh to tenth centuries.",
        rare = true,
    ),

)
