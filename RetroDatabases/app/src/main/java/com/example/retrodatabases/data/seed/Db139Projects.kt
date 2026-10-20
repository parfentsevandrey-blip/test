package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One project of 650 to 660: the Tang era names Yonghui and Xianqing, split across the eleven years of the decade and counted year by year. */
val db139Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 139,
        kind = Kind.PROJECT,
        section = "Chinese",
        title = "The Tang era names Yonghui and Xianqing across 650 to 660, the eleven years counted year by year in code",
        originalTitle = "Yonghui and Xianqing",
        author = "Emperor Gaozong",
        year = 650,
        summary = "The eleven years from 650 to 660 are split between the Tang era names Yonghui, which runs to 655, and Xianqing, which begins in 656, and a short program counts each share.",
        details = "The Yonghui era is commonly given as 650 to 655, and the Xianqing era begins in 656 and runs past the decade, so six years fall to Yonghui and five to Xianqing inside the decade. The program adds the two shares and checks them against the eleven years of 650 to 660 inclusive.",
        rare = true,
        instructions = """
            1. Save the example as tang_eras.py.
            2. Run it: python3 tang_eras.py
        """,
        example = """
            ERAS = [
                ("Yonghui", 650, 655),
                ("Xianqing", 656, 661),
            ]
            FIRST, LAST = 650, 660

            total = 0
            for name, start, end in ERAS:
                years = [year for year in range(FIRST, LAST + 1) if start <= year <= end]
                total += len(years)
                print(name, "years in the decade:", len(years), "from", years[0], "to", years[-1])
            print("years covered by the two eras:", total)
            print("years in the decade 650 to 660:", LAST - FIRST + 1)
        """,
    ),

)
