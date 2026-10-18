package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 1041 to 1048: the calendar years of the Song reign periods Kangding and Qingli, counted and added up. */
val db100Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 100,
        kind = Kind.PROJECT,
        section = "Dating",
        title = "The Song reign periods Kangding and Qingli, with their calendar years counted",
        originalTitle = "The Song reign periods Kangding and Qingli, with their calendar years counted",
        author = "Song imperial chancery",
        year = 1041,
        summary = "A short program counts the calendar years of the Song reign periods Kangding and Qingli, which together cover the years 1040 to 1048, and adds them up.",
        details = "Kangding is dated to 1040 and 1041 and Qingli to 1041 to 1048, so the two periods share the year 1041. The next period, Huangyou, is left out because accounts differ on when it ended, giving 1053 in one list and 1054 in another.",
        instructions = """
            1. Save the example as song_periods.py.
            2. Run it: python3 song_periods.py
        """,
        example = """
            # reign periods of the Song with their first and last calendar years, as commonly given
            periods = [("Kangding", 1040, 1041), ("Qingli", 1041, 1048)]
            total = 0
            longest = None
            shortest = None
            for name, first, last in periods:
                years = last - first + 1
                total += years
                print(name, first, last, years)
                if longest is None or years > longest[1]:
                    longest = (name, years)
                if shortest is None or years < shortest[1]:
                    shortest = (name, years)
            print("periods:", len(periods))
            print("total years:", total)
            print("longest:", longest[0], longest[1], "years")
            print("shortest:", shortest[0], shortest[1], "years")
        """,
    ),

)
