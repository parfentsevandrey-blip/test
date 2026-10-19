package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 810 to 820: a small program that counts the rulers who died in office in the 810s by the year of their deaths. */
val db123Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 123,
        kind = Kind.PROJECT,
        section = "Rulers",
        title = "Six rulers who died in office in the 810s, counted by the year of their deaths",
        originalTitle = "Six rulers who died in office in the 810s, counted by the year of their deaths",
        author = "Modern compiler from standard dates",
        year = 810,
        summary = "A short program lists six rulers who died in office in the 810s and prints how many died in each year, and 814 and 820 each saw two deaths.",
        details = "The rulers are Pepin of Italy, Nikephoros I of Byzantium, Krum of Bulgaria, Charlemagne, the Tang emperor Xianzong and Leo V of Byzantium, and the program groups their death years. The years used are the usual dates of death, and 810, the year of Pepin's death, is the earliest of them.",
        rare = true,
        instructions = """
            1. Save the example as deaths_1810s.py.
            2. Run it: python3 deaths_1810s.py
        """,
        example = """
            deaths = [
                ("Pepin of Italy", 810),
                ("Nikephoros I", 811),
                ("Krum", 814),
                ("Charlemagne", 814),
                ("Xianzong of Tang", 820),
                ("Leo V", 820),
            ]
            by_year = {}
            for name, year in deaths:
                by_year.setdefault(year, []).append(name)
            for year in sorted(by_year):
                print(year, ":", len(by_year[year]), "death(s)")
            print("rulers in all:", len(deaths))
            print("years with two deaths:", [y for y in sorted(by_year) if len(by_year[y]) == 2])
        """,
    ),

)
