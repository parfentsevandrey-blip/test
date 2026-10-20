package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One tool of 500 to 510: Varahamihira's Panchasiddhantika, a handbook of five earlier astronomical treatises, dated about 505. */
val db154Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 154,
        kind = Kind.TOOL,
        section = "Sanskrit",
        title = "Varahamihira's Panchasiddhantika, a handbook gathering five earlier Indian astronomical treatises, about 505",
        originalTitle = "Pancasiddhantika",
        author = "Varahamihira",
        year = 505,
        summary = "Varahamihira's handbook gathered five earlier Indian astronomical treatises into one compact set of rules for computing the positions of the sky.",
        details = "Its date is inferred rather than stated: a Shaka-era year in the text, 427, is 505 CE, which scholars take as the time the book was finished or conceived. Some readings doubt that date or treat 505 as another milestone, so accounts differ, and this record uses 505.",
        rare = true,
    ),

)
