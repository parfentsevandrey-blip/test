package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One project of 860 to 870: a short program that looks up the rulers who held power in 865 in a table of reigns. */
val db118Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 118,
        kind = Kind.PROJECT,
        section = "Rulers",
        title = "The rulers who held power in 865, looked up in a table of reigns",
        originalTitle = "Table of reigns for 865",
        author = "Reign dates of eleven rulers, as commonly given",
        year = 865,
        summary = "A short program looks up eleven rulers in a table of reigns and prints the eight whose reigns cover the year 865.",
        details = "The table holds eight rulers whose reigns cover 865, Michael III, Louis II, Charles the Bald, Louis the German, Nicholas I, Yizong, Seiwa and al-Musta'in, and three who began later, al-Mu'tazz, Hadrian II and Basil I, which test the filter. The start and end years are taken as inclusive, so a ruler who died in 866 still counts for 865.",
        instructions = """
            1. Save the example as rulers_865.py.
            2. Run it: python3 rulers_865.py
        """,
        example = """
            YEAR = 865
            # name, first year and last year of the reign, inclusive, as commonly given
            rulers = [
                ("Michael III (Byzantium)", 842, 867),
                ("Louis II (Italy)", 855, 875),
                ("Charles the Bald (West Francia)", 840, 877),
                ("Louis the German (East Francia)", 843, 876),
                ("Nicholas I (papacy)", 858, 867),
                ("Yizong (Tang China)", 859, 873),
                ("Seiwa (Japan)", 858, 876),
                ("al-Musta'in (Abbasid caliphate)", 862, 866),
                ("al-Mu'tazz (Abbasid caliphate)", 866, 869),
                ("Hadrian II (papacy)", 867, 872),
                ("Basil I (Byzantium)", 867, 886),
            ]
            in_power = [r for r in rulers if r[1] <= YEAR <= r[2]]
            print("rulers in power in", YEAR, ":", len(in_power))
            for name, first, last in in_power:
                print(name, first, last)
        """,
    ),

)
