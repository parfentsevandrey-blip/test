package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 190 to 200: the star compass of Pacific navigators, undated in the sources and placed at 190 by the Muratorian fragment. */
val db185Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 185,
        kind = Kind.PRACTICE,
        section = "Navigation",
        title = "The star compass of Pacific navigators, a memorized ring of rising and setting stars",
        originalTitle = "Star compass",
        author = "Pacific navigators",
        year = 190,
        summary = "Pacific navigators memorized the points on the horizon where stars rise and set, so as to hold a course at night, and this undated practice is placed at 190 with the Muratorian fragment as its anchor.",
        details = "The sources do not date the practice, and the written descriptions come from European visitors of later centuries, so 190 is only the year of the anchor and not the date of the practice. No link between the Muratorian fragment and the navigators is claimed, and the year is used only to place the record within the decade.",
        rare = true,
    ),

)
