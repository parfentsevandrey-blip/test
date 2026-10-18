package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 980 to 990: the accession years of five rulers, sorted and spaced by a short program. */
val db106Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 106,
        kind = Kind.PROJECT,
        section = "Rulers",
        title = "The accession years of five rulers of the 980s, put in order with their gaps",
        originalTitle = "The accession years of five rulers of the 980s, put in order with their gaps",
        author = "Five rulers of the 980s",
        year = 987,
        summary = "A short program sorts the accession years of five rulers of the 980s and prints the gap between each pair of neighbours in the list.",
        details = "The rulers are Le Hoan, Otto III, the Japanese emperor Kazan, Rajaraja I and Hugh Capet, and each year is the year that ruler came to power. The gaps show how the accessions followed one another across the decade.",
        instructions = """
            1. Save the example as rulers.py.
            2. Run it: python3 rulers.py
        """,
        example = """
            rulers = [
                ("Hugh Capet", 987),
                ("Le Hoan", 980),
                ("Rajaraja I", 985),
                ("Otto III", 983),
                ("Kazan", 984),
            ]
            rulers.sort(key=lambda pair: pair[1])
            previous = None
            for name, year in rulers:
                gap = "" if previous is None else f" (+{year - previous})"
                print(f"{year} {name}{gap}")
                previous = year
            print("rulers:", len(rulers))
            print("span:", rulers[-1][1] - rulers[0][1], "years")
        """,
    ),

)
