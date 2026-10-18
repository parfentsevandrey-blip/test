package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Al-Farabi's catalogue of the sciences, placed at 950, the year of his death, since no dated tool of the decade is secured. */
val db110Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 110,
        kind = Kind.TOOL,
        section = "Islamic learning",
        title = "Al-Farabi's catalogue of the sciences, the Ihsa al-ulum",
        originalTitle = "Ihsa al-ulum",
        author = "al-Farabi",
        year = 950,
        summary = "Al-Farabi's Ihsa al-ulum sorts the sciences into their branches, and the record is placed at 950, the year of his death.",
        details = "The sources do not date the catalogue, so 950 is the placed year: it is the year commonly given for al-Farabi's death at Damascus, a date that some accounts move to 951. The book classifies learning into the sciences and their branches.",
        rare = true,
    ),

)
