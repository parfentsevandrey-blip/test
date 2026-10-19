package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One project of 740 to 750: the Umayyad caliphs from 724 to 750, each reign counted from one accession to the next by a short program. */
val db130Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 130,
        kind = Kind.PROJECT,
        section = "Umayyad",
        title = "The Umayyad caliphs of 724 to 750, each reign counted from one accession to the next",
        originalTitle = "Hisham, al-Walid II, Yazid III, Ibrahim and Marwan II",
        author = "The Umayyad caliphs",
        year = 743,
        summary = "Five Umayyad caliphs ruled between 724 and 750, and a short program counts each reign from accession to the next and ranks the reigns.",
        details = "Hisham died in 743 and was followed by al-Walid II, Yazid III, Ibrahim and Marwan II, the last Umayyad caliph, who fell in 750. A reign that begins and ends in one year counts as one year, so the figures show the order of the accessions and not the exact months of rule.",
        instructions = """
            1. Save the example as umayyad_caliphs.py.
            2. Run it: python3 umayyad_caliphs.py
        """,
        example = """
            CALIPHS = [
                ("Hisham", 724, 743),
                ("al-Walid II", 743, 744),
                ("Yazid III", 744, 744),
                ("Ibrahim", 744, 744),
                ("Marwan II", 744, 750),
            ]
            spans = {name: max(1, end - start) for name, start, end in CALIPHS}
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
