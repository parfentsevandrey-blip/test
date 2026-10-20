package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One project of 550 to 560: the founding years of seven dynasties of the Northern and Southern period, sorted and compared in code. */
val db149Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 149,
        kind = Kind.PROJECT,
        section = "Chinese",
        title = "The founding years of seven Chinese dynasties of the Northern and Southern period, from 386 to 557, sorted and compared in code",
        originalTitle = "Nanbei chao",
        author = "Chinese dynastic chroniclers",
        year = 557,
        summary = "A short program lists the founding years of seven dynasties of the Northern and Southern period, from Northern Wei in 386 to Chen and Northern Zhou in 557, and prints the gaps between them.",
        details = "The founding dates are conventions: the Northern Wei is counted from its revival under Tuoba Gui in 386, although its ruler took the imperial title in 399, and the other dates are the usual ones for each state. Chen and Northern Zhou both begin in 557, so one gap is zero, and the longest gap, 59 years between Liu Song and Southern Qi, is printed on its own line.",
        rare = true,
        instructions = """
            1. Save the example as dynasty_years.py.
            2. Run it: python3 dynasty_years.py
        """,
        example = """
            FOUNDED = [
                ("Northern Wei", 386),
                ("Liu Song", 420),
                ("Southern Qi", 479),
                ("Liang", 502),
                ("Northern Qi", 550),
                ("Chen", 557),
                ("Northern Zhou", 557),
            ]
            ordered = sorted(FOUNDED, key=lambda item: (item[1], item[0]))
            gaps = []
            for (name_a, year_a), (name_b, year_b) in zip(ordered, ordered[1:]):
                gaps.append((year_b - year_a, name_a, name_b))
                print(name_a, "to", name_b + ":", year_b - year_a, "years")
            print("dynasties:", len(ordered))
            first, last = ordered[0][1], ordered[-1][1]
            print("span from", first, "to", last, "is", last - first, "years")
            longest = max(gaps)
            print("longest gap:", longest[1], "to", longest[2], "is", longest[0], "years")
            print("sum of gaps:", sum(gap for gap, _, _ in gaps))
        """,
    ),

)
