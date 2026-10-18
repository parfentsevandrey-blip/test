package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 910 to 920: the reign lengths of the Cordoban emirs before Abd al-Rahman III, summed and ranked. */
val db113Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 113,
        kind = Kind.PROJECT,
        section = "Spain",
        title = "Reign lengths of the seven Cordoban emirs before Abd al-Rahman III, summed and ranked",
        originalTitle = "Reign lengths of the seven Cordoban emirs before Abd al-Rahman III, summed and ranked",
        author = "Umayyad emirs of Cordoba",
        year = 912,
        summary = "The seven Umayyad emirs who ruled Cordoba before Abd al-Rahman III held power for 156 years in all, and the program sums and ranks their reigns.",
        details = "Abd Allah's reign ended in 912, the year Abd al-Rahman III came to power, so the list stops there. Each reign length is the end year minus the start year, and the output ranks the reigns from Muhammad I, with 34 years, down to al-Mundhir, with 2.",
        rare = true,
        instructions = """
            1. Save the example as emirs.py.
            2. Run it: python3 emirs.py
        """,
        example = """
            # reigns of the Umayyad emirs of Cordoba, from start year to end year
            emirs = [
                ("Abd al-Rahman I", 756, 788),
                ("Hisham I", 788, 796),
                ("al-Hakam I", 796, 822),
                ("Abd al-Rahman II", 822, 852),
                ("Muhammad I", 852, 886),
                ("al-Mundhir", 886, 888),
                ("Abd Allah", 888, 912),
            ]
            lengths = [(end - start, name) for name, start, end in emirs]
            print("emirs:", len(lengths))
            print("total years:", sum(length for length, _ in lengths))
            for length, name in sorted(lengths, reverse=True):
                print(name, length)
        """,
    ),

)
