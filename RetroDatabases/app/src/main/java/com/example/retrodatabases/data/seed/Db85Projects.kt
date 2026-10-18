package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 1189 to 1199: the deaths of five rulers in date order, and the days between three English coronations. */
val db85Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 85,
        kind = Kind.PROJECT,
        section = "Crusades",
        title = "Deaths of five rulers from 1189 to 1199, in date order",
        originalTitle = "Deaths in date order",
        author = "No single author",
        year = 1199,
        summary = "Five rulers of the late twelfth century died between 1189 and 1199, and a short program sorts their deaths by date and counts the days between them.",
        details = "The dates are the usual ones: Henry II in July 1189, Frederick Barbarossa in June 1190, Saladin in March 1193, Henry VI in September 1197 and Richard I in April 1199. The program uses Python's calendar, and its day counts match the Julian reckoning of the time, because no century year falls between these dates.",
        instructions = """
            1. Save the example as deaths.py.
            2. Run it: python3 deaths.py
        """,
        example = """
            from datetime import date

            deaths = [
                ("Henry II", date(1189, 7, 6)),
                ("Frederick Barbarossa", date(1190, 6, 10)),
                ("Saladin", date(1193, 3, 4)),
                ("Henry VI", date(1197, 9, 28)),
                ("Richard I", date(1199, 4, 6)),
            ]
            deaths.sort(key=lambda item: item[1])
            previous = None
            for name, day in deaths:
                if previous is None:
                    print(day.isoformat(), name)
                else:
                    print(day.isoformat(), name, (day - previous).days, "days later")
                previous = day
        """,
    ),

    entry(
        databaseId = 85,
        kind = Kind.PROJECT,
        section = "Coronations",
        title = "Coronations of three English kings, counted in days",
        originalTitle = "Coronation dates",
        author = "No single author",
        year = 1199,
        summary = "Henry II, Richard I and John were each crowned at Westminster between 1154 and 1199, and a short program counts the days between their coronations.",
        details = "The dates are 19 December 1154, 3 September 1189 and 27 May 1199, as commonly given, and the day counts hold in both the Julian and the Gregorian reckonings because no century year lies between them. The program lists each king with the days that passed before the next coronation.",
        instructions = """
            1. Save the example as coronations.py.
            2. Run it: python3 coronations.py
        """,
        example = """
            from datetime import date

            crownings = [
                ("Henry II", date(1154, 12, 19)),
                ("Richard I", date(1189, 9, 3)),
                ("John", date(1199, 5, 27)),
            ]
            for (first_name, first), (second_name, second) in zip(crownings, crownings[1:]):
                print(first_name, "to", second_name, (second - first).days, "days")
        """,
    ),

)
