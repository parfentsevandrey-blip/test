package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 1120 to 1130: the popes, Japanese eras and Song emperors of the decade, each pontificate, era or reign counted in years. */
val db92Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 92,
        kind = Kind.PROJECT,
        section = "Popes",
        title = "The popes of the 1120s, with each pontificate counted in years",
        originalTitle = "Pontificates of Callixtus II, Honorius II and Innocent II",
        author = "Popes Callixtus II, Honorius II and Innocent II",
        year = 1124,
        summary = "A short program counts the years of three popes from Callixtus II to Innocent II, and its total gives a span of twenty-four years from 1119 to 1143.",
        details = "Each pontificate is counted from the year of election to the year of death, in whole years, so the figures are a convention rather than exact lengths. Callixtus II died in 1124, Honorius II in 1130, and Innocent II in 1143.",
        instructions = """
            1. Save the example as popes_years.py.
            2. Run it: python3 popes_years.py
        """,
        example = """
            popes = [("Callixtus II", 1119, 1124), ("Honorius II", 1124, 1130), ("Innocent II", 1130, 1143)]
            for name, start, end in popes:
                print(name, end - start)
            print("total years:", sum(end - start for name, start, end in popes))
            print("span:", popes[0][1], "to", popes[-1][2])
        """,
    ),

    entry(
        databaseId = 92,
        kind = Kind.PROJECT,
        section = "Japan",
        title = "The Japanese eras of the 1120s, with the years each one lasted",
        originalTitle = "Hoan, Tenji and Daiji eras",
        author = "The Japanese court",
        year = 1120,
        summary = "The Japanese eras Hoan, Tenji and Daiji follow one another from 1120 to 1131, and a short program counts the years of each.",
        details = "Each era is counted from its first year to the first year of the next, which gives whole years by convention. The three eras cover eleven years in all, from 1120 to 1131.",
        instructions = """
            1. Save the example as eras_years.py.
            2. Run it: python3 eras_years.py
        """,
        example = """
            eras = [("Hoan", 1120, 1124), ("Tenji", 1124, 1126), ("Daiji", 1126, 1131)]
            for name, start, end in eras:
                print(name, end - start)
            print("total years:", sum(end - start for name, start, end in eras))
            print("span:", eras[0][1], "to", eras[-1][2])
        """,
        rare = true,
    ),

    entry(
        databaseId = 92,
        kind = Kind.PROJECT,
        section = "China",
        title = "The Song emperors through the 1120s, with each reign counted in years",
        originalTitle = "Reigns of Huizong, Qinzong and Gaozong",
        author = "The Song court",
        year = 1126,
        summary = "A short program counts the years of three Song emperors from Huizong to Gaozong, and the total gives a span of sixty-two years from 1100 to 1162.",
        details = "Huizong's reign is counted from 1100 to 1126, Qinzong's from 1126 to 1127 and Gaozong's from 1127 to 1162, each as a whole number of years. The counts are conventions, because the years do not show the months when each reign began and ended.",
        instructions = """
            1. Save the example as song_years.py.
            2. Run it: python3 song_years.py
        """,
        example = """
            rulers = [("Huizong", 1100, 1126), ("Qinzong", 1126, 1127), ("Gaozong", 1127, 1162)]
            for name, start, end in rulers:
                print(name, end - start)
            print("total years:", sum(end - start for name, start, end in rulers))
            print("span:", rulers[0][1], "to", rulers[-1][2])
        """,
    ),

)
