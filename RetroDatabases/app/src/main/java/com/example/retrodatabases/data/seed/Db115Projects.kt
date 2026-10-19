package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One project of 890 to 900: a short program over six imperial coronations at Rome from 875 to 915, printing the gaps between them. */
val db115Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 115,
        kind = Kind.PROJECT,
        section = "Europe",
        title = "Six imperial coronations at Rome from 875 to 915, with the gaps between them summed",
        originalTitle = "Imperial coronations at Rome",
        author = "The popes who crowned the emperors",
        year = 891,
        summary = "A short program lists six coronations of western emperors at Rome from 875 to 915 and prints the gaps between them.",
        details = "The list runs from Charles the Bald in 875 through Charles the Fat, Guy of Spoleto, Arnulf of Carinthia and Louis III to Berengar I in 915, and the gaps total 40 years, the longest being the 14 years from Louis III to Berengar I. The year 891, Guy of Spoleto's coronation, is the first inside the decade, which is why the record is dated to it.",
        instructions = """
            1. Save the example as coronations.py.
            2. Run it: python3 coronations.py
        """,
        example = """
            coronations = [
                ("Charles the Bald", 875),
                ("Charles the Fat", 881),
                ("Guy of Spoleto", 891),
                ("Arnulf of Carinthia", 896),
                ("Louis III of Provence", 901),
                ("Berengar I", 915),
            ]
            years = [year for _, year in coronations]
            gaps = [later - earlier for earlier, later in zip(years, years[1:])]
            print("coronations:", len(coronations))
            print("gaps:", gaps)
            print("total span:", years[-1] - years[0], "years")
            longest = max(range(len(gaps)), key=lambda i: gaps[i])
            print("longest gap: from", coronations[longest][0], "to", coronations[longest + 1][0], gaps[longest], "years")
        """,
    ),

)
