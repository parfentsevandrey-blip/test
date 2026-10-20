package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 120 to 130: three deaths of the decade from China and Egypt, sorted and spaced by a short program. */
val db192Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 192,
        kind = Kind.PROJECT,
        section = "Deaths",
        title = "Three deaths of 121 to 130 in China and Egypt, sorted and spaced in code",
        originalTitle = "Three deaths of 121 to 130 in China and Egypt, sorted and spaced in code",
        author = "No single author",
        year = 121,
        summary = "Three deaths of 121 to 130 are sorted by year, and a short program prints the gaps between them and the span of the three.",
        details = "Empress Dowager Deng Sui died in April 121, Emperor An of Han died in 125, and Antinous drowned in the Nile in October 130. The years are the ones the accounts give, and the example prints the sorted list and the gaps, so the span of 9 years can be checked by hand.",
        instructions = """
            1. Save the example as deaths.py.
            2. Run it: python3 deaths.py
        """,
        example = """
            deaths = [
                ("Antinous drowned in the Nile", 130),
                ("Empress Dowager Deng Sui died", 121),
                ("Emperor An of Han died", 125),
            ]
            ordered = sorted(deaths, key=lambda item: item[1])
            for name, year in ordered:
                print(year, name)
            for earlier, later in zip(ordered, ordered[1:]):
                print("gap from", earlier[1], "to", later[1], "is", later[1] - earlier[1], "years")
            print("span in years:", ordered[-1][1] - ordered[0][1])
        """,
        rare = true,
    ),

)
