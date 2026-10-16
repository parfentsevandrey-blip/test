package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 1330 to 1338: Kenmu years, a quadrant's scale marks, a poet's age, ottava rima, Hijri years and sand-glass turns. */
val db71Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 71,
        kind = Kind.EXAMPLE,
        section = "Japan",
        title = "Years of the Kenmu era turned into Christian years",
        originalTitle = "Kenmu era",
        author = "The court of Emperor Go-Daigo",
        year = 1336,
        summary = "The Kenmu era began in 1334, so a Kenmu year plus 1333 gives the Christian year, and a short program converts the first three Kenmu years.",
        details = "Kenmu was the era name chosen at the court of Emperor Go-Daigo in the mid-1330s, and its third year is 1336, the year of the Kenmu code. The example prints the three years and the span between the first and the third.",
        instructions = """
            1. Save the example as kenmu.py.
            2. Run it: python3 kenmu.py
        """,
        example = """
            # Kenmu year 1 is 1334, so a Kenmu year plus 1333 gives the Christian year.
            for kenmu_year in (1, 2, 3):
                print("Kenmu", kenmu_year, "is", 1333 + kenmu_year)
            print("years between Kenmu 1 and Kenmu 3:", (1333 + 3) - (1333 + 1))
        """,
    ),

    entry(
        databaseId = 71,
        kind = Kind.EXAMPLE,
        section = "Astronomy",
        title = "Scale marks on a quadrant at every five minutes of arc",
        originalTitle = "Quadrant scale",
        author = "Makers of astrolabic quadrants",
        year = 1333,
        summary = "A quadrant's scale runs through ninety degrees, and a short program counts the marks set at every five minutes of arc, which gives 1081 marks with both ends included.",
        details = "The scale is the arc on which the altitude is read, and its marks are set at equal steps of arc. The example prints the minutes of arc on the scale, the intervals, the marks with both ends included and the marks in each degree, so the counts can be checked by hand.",
        instructions = """
            1. Save the example as quadrant_marks.py.
            2. Run it: python3 quadrant_marks.py
        """,
        example = """
            DEGREES = 90
            STEP_MINUTES = 5
            minutes = DEGREES * 60
            print("minutes of arc on the scale:", minutes)
            print("intervals of five minutes:", minutes // STEP_MINUTES)
            print("marks including both ends:", minutes // STEP_MINUTES + 1)
            print("marks in each degree:", 60 // STEP_MINUTES)
        """,
    ),

    entry(
        databaseId = 71,
        kind = Kind.EXAMPLE,
        section = "Lives",
        title = "Petrarch's age on the day of his climb of Mont Ventoux",
        originalTitle = "Age in years and days",
        author = "Francesco Petrarca",
        year = 1336,
        summary = "Petrarch was born on 20 July 1304, so on his climb of Mont Ventoux on 26 April 1336 he was thirty-one years old and had lived 11603 days, which a short program works out.",
        details = "The birth date is given as commonly stated, and the example uses Python's calendar for both dates, which gives the same count here because no century year falls in the span. The program prints the age in whole years and the number of days lived.",
        instructions = """
            1. Save the example as petrarch_age.py.
            2. Run it: python3 petrarch_age.py
        """,
        example = """
            from datetime import date

            born = date(1304, 7, 20)     # Petrarch's birth, as commonly given
            climb = date(1336, 4, 26)    # the climb of Mont Ventoux, as commonly given
            years = climb.year - born.year - ((climb.month, climb.day) < (born.month, born.day))
            print("age in whole years:", years)
            print("days lived:", (climb - born).days)
        """,
    ),

    entry(
        databaseId = 71,
        kind = Kind.EXAMPLE,
        section = "Poetry",
        title = "Syllables in ottava rima stanzas of a canto",
        originalTitle = "Ottava rima",
        author = "Boccaccio and later Italian poets",
        year = 1335,
        summary = "An ottava rima stanza has eight lines of eleven syllables with the rhyme scheme ABABABCC, and a short program counts the lines and syllables for three canto lengths.",
        details = "Boccaccio's Filostrato is written in this stanza, and the example counts lines and syllables for one stanza, ten stanzas and twelve stanzas. The output can be checked by hand from the line and syllable counts.",
        instructions = """
            1. Save the example as ottava.py.
            2. Run it: python3 ottava.py
        """,
        example = """
            LINES_PER_STANZA = 8
            SYLLABLES_PER_LINE = 11
            RHYME = "ABABABCC"
            for stanzas in (1, 10, 12):
                lines = stanzas * LINES_PER_STANZA
                print(stanzas, "stanzas:", lines, "lines,", lines * SYLLABLES_PER_LINE, "syllables")
            print("rhyme scheme:", RHYME)
        """,
    ),

    entry(
        databaseId = 71,
        kind = Kind.EXAMPLE,
        section = "Calendars",
        title = "Hijri years of the 1330s turned into Christian years, roughly",
        originalTitle = "Hijri calendar",
        author = "Islamic chroniclers and astronomers",
        year = 1334,
        summary = "A Hijri year is turned roughly into a Christian year by subtracting a thirty-third part and adding 621, and a short program converts three years around the 1330s.",
        details = "The rule is an approximation for the Islamic lunar calendar and is correct to within about a year, which is enough to place events of the period. It gives 1333 for the Hijri year 734, the year of the Damascus quadrant's production window.",
        instructions = """
            1. Save the example as hijri.py.
            2. Run it: python3 hijri.py
        """,
        example = """
            # Rough rule: the Christian year is the Hijri year minus a thirty-third part of it, plus 621.
            for hijri in (730, 734, 740):
                print("AH", hijri, "is about", hijri - hijri // 33 + 621)
        """,
    ),

    entry(
        databaseId = 71,
        kind = Kind.EXAMPLE,
        section = "Time",
        title = "Turns of a sand-glass through a vigil and a sermon",
        originalTitle = "Sand-glass",
        author = "Sextons and preachers",
        year = 1338,
        summary = "A one-hour sand-glass is turned once for each hour, and a short program counts the turns for a vigil of three hours, a sermon of two hours and a day of twelve hours.",
        details = "Sand-glasses of the period were turned by hand and measured time in hours, as the hourglass in Lorenzetti's fresco shows. The example prints the turns for each timed event so that the count can be checked by hand.",
        instructions = """
            1. Save the example as sand_glass.py.
            2. Run it: python3 sand_glass.py
        """,
        example = """
            GLASS_HOURS = 1   # one hour for each glass, illustrative
            for name, hours in (("vigil", 3), ("sermon", 2), ("day of light", 12)):
                print(name, hours, "hours:", hours // GLASS_HOURS, "turns of the glass")
        """,
    ),

)
