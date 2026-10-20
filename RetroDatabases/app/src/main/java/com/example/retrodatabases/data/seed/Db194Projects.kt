package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 100 to 110: four dated turns of Trajan's Dacian wars, from 101 to 106, sorted and spaced in code. */
val db194Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 194,
        kind = Kind.PROJECT,
        section = "Wars",
        title = "Four turns of Trajan's Dacian wars from 101 to 106, sorted and spaced in code",
        originalTitle = "Trajan's Dacian wars",
        author = "No single author",
        year = 101,
        summary = "Four dated turns of Trajan's Dacian wars, from 101 to 106, are sorted and spaced by a short program that prints the years and the gaps between them.",
        details = "The first war ran from 101 to 102, the second from 105 to 106, and Dacia was made a Roman province in 106 at the end of the second war. The example uses years only, so no day is needed, and the program prints the gaps so the spacing can be checked by hand.",
        instructions = """
            1. Save the example as dacian_wars.py.
            2. Run it: python3 dacian_wars.py
        """,
        example = """
            events = [
                (105, "second Dacian war begins"),
                (101, "first Dacian war begins"),
                (106, "Dacia becomes a Roman province"),
                (102, "first Dacian war ends"),
            ]
            events.sort()
            for year, text in events:
                print(year, text)
            gaps = [later - earlier for (earlier, _), (later, _) in zip(events, events[1:])]
            print("gaps in years:", gaps)
            print("total span:", events[-1][0] - events[0][0])
        """,
        rare = true,
    ),

)
