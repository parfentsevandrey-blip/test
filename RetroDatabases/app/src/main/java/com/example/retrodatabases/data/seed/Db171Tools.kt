package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One tool of 330 to 340: the glassblower's blowpipe, placed at 335 because the sources do not date its first use. */
val db171Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 171,
        kind = Kind.TOOL,
        section = "Glass",
        title = "The glassblower's blowpipe, a hollow metal tube for gathering and blowing molten glass",
        originalTitle = "blowpipe",
        author = "Glassmakers of the eastern Mediterranean",
        year = 335,
        summary = "The blowpipe is a hollow metal tube that glassmakers use to gather molten glass and blow it into shape, and it is placed here at 335 for the Council of Tyre.",
        details = "Archaeological evidence points to glassblowing in Syria and Palestine in the first century BCE, so the tool is much older than this year. The sources do not date the blowpipe; 335 is the year of the anchor, the Council of Tyre, and not the year of the making.",
        rare = true,
    ),

)
