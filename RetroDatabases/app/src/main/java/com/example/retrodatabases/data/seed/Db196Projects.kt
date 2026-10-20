package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 80 to 89: four dated events of the decade, sorted and spaced in a short program. */
val db196Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 196,
        kind = Kind.PROJECT,
        section = "Dates",
        title = "Four dated events of 80 to 89, sorted and spaced in code",
        originalTitle = "Four dates in order",
        author = "No single author",
        year = 89,
        summary = "The opening of the Flavian amphitheatre, the death of Titus, the first Capitoline games and the battle of Yan Mountain are sorted and spaced by a short program.",
        details = "The years are the usual ones: the opening is often given as 80, though some accounts say 81, and the death of Titus is usually placed in 81. The Capitoline games are usually dated to 86 and the battle of Yan Mountain to 89, and the program prints the span and the gaps so the arithmetic can be checked.",
        instructions = """
            1. Save the example as dated_events.py.
            2. Run it: python3 dated_events.py
        """,
        example = """
            events = [
                (89, "battle of Yan Mountain"),
                (80, "opening of the Flavian amphitheatre"),
                (86, "first Capitoline games"),
                (81, "death of Titus"),
            ]
            events.sort()
            years = [year for year, _ in events]
            for year, name in events:
                print(year, name)
            print("span in years:", years[-1] - years[0])
            print("gaps:", [later - earlier for earlier, later in zip(years, years[1:])])
        """,
    ),

)
