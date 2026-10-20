package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One guide of 421: how a seal is cut in reverse so that its impression reads correctly, placed at 421, the year Constantius III became co-emperor. */
val db162Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 162,
        kind = Kind.GUIDE,
        section = "Seals",
        title = "How a seal is cut in reverse so that its impression reads correctly, placed at 421 for the year Constantius III became co-emperor",
        originalTitle = "Seal cutting",
        author = "Seal cutters of the imperial and provincial offices",
        year = 421,
        summary = "A seal is cut as the mirror image of the text it stamps, and a short program reverses a carved line and then reverses it again to read the impression.",
        details = "The sources do not date this method, so 421 is the year of the anchor, the elevation of Constantius III as co-emperor, and not the year the rule was first used. The mirror rule holds for any seal, and the example uses the name Liu Song only as a sample text.",
        rare = true,
        instructions = """
            1. Save the example as seal_reverse.py.
            2. Run it: python3 seal_reverse.py
        """,
        example = """
            impression = "LIU SONG"
            carved = impression[::-1]
            print("impression:", impression)
            print("carved line:", carved)
            print("stamp of the carved line:", carved[::-1])
            print("letters in the seal:", len(impression.replace(" ", "")))
        """,
    ),

)
