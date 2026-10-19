package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One project of 710 to 720: the reigns of three Umayyad caliphs from 705 to 720, the years each one ruled, summed and ranked by a short program. */
val db133Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 133,
        kind = Kind.PROJECT,
        section = "Umayyad",
        title = "The reigns of Walid I, Sulayman and Umar II from 705 to 720, summed and ranked",
        originalTitle = "Umayyad reigns from 705 to 720",
        author = "The Umayyad caliphs",
        year = 720,
        summary = "Three Umayyad caliphs ruled between 705 and 720, Walid I for 10 years, Sulayman for 2 and Umar II for 3, and a short program sums and ranks those spans.",
        details = "Walid I came to the throne in 705 and died in 715, Sulayman ruled from 715 to 717, and Umar II from 717 until his death in 720, so the count stops there. The spans use the usual dates of accession and death, and Walid's reign, under which Tariq and Musa ibn Nusayr began the conquest of Spain, is the longest of the three.",
        rare = true,
        instructions = """
            1. Save the example as umayyad_reigns.py.
            2. Run it: python3 umayyad_reigns.py
        """,
        example = """
            REIGNS = [
                ("Walid I", 705, 715),
                ("Sulayman", 715, 717),
                ("Umar II", 717, 720),
            ]

            spans = {name: end - start for name, start, end in REIGNS}
            for name, span in spans.items():
                print(name, span, "years")
            print("total:", sum(spans.values()), "years")
            longest = max(spans, key=spans.get)
            shortest = min(spans, key=spans.get)
            print("longest:", longest, spans[longest], "years")
            print("shortest:", shortest, spans[shortest], "years")
        """,
    ),

)
