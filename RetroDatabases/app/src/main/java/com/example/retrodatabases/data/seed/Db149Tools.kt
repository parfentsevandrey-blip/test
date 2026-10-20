package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One tool of 550 to 560: Varahamihira's Brihat Samhita, an Indian encyclopedia dated to the sixth century and given the year 550. */
val db149Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 149,
        kind = Kind.TOOL,
        section = "Indian",
        title = "Varahamihira's Brihat Samhita, an encyclopedia of astronomy, astrology and architecture, dated to the sixth century and here to 550",
        originalTitle = "Brhat-samhita",
        author = "Varahamihira",
        year = 550,
        summary = "Varahamihira's Brihat Samhita is a Sanskrit encyclopedia of astronomy, astrology, omens and architecture, dated to the sixth century and given the year 550 here.",
        details = "The date is not fixed: scholars place Varahamihira in the sixth century, possibly reaching back into the late fifth, and the work is taken to be sixth-century with later additions, so 550 is an approximate year rather than a recorded one. Its chapters were copied, commented on and used by astrologers and builders for many centuries after.",
        rare = true,
    ),

)
