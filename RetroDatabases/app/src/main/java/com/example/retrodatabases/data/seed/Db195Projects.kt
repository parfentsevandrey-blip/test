package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One project of 90 to 100: four dated events of the decade, sorted and spaced in code. */
val db195Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 195,
        kind = Kind.PROJECT,
        section = "Timelines",
        title = "Four events of 92 to 98 from Rome and Han China, sorted and spaced in code",
        originalTitle = "Four dated events, sorted",
        author = "No single author",
        year = 98,
        summary = "The death of Ban Gu, the assassination of Domitian, the mission of Gan Ying and the death of Nerva are sorted and spaced by a short program.",
        details = "The program works in whole years, so the gaps it prints do not depend on the calendar. Domitian's assassination is dated 18 September 96 in most references and Nerva's death 27 January 98, and the death of Ban Gu in prison, in 92, comes from the Han history as usually given.",
        rare = true,
        instructions = """
            1. Save the example as han_rome_events.py.
            2. Run it: python3 han_rome_events.py
        """,
        example = """
            events = [
                (98, "Nerva dies"),
                (92, "Ban Gu dies in prison"),
                (97, "Gan Ying sent toward Daqin"),
                (96, "Domitian is assassinated"),
            ]
            events.sort()
            for index, (year, label) in enumerate(events):
                if index + 1 < len(events):
                    gap = events[index + 1][0] - year
                    print(year, label, "gap to next:", gap)
                else:
                    print(year, label)
            print("span in years:", events[-1][0] - events[0][0])
        """,
    ),

)
