package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** A project of 943: the reign years of the Samanid amirs Nasr II and Nuh I, summed and ranked by a short program. */
val db110Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 110,
        kind = Kind.PROJECT,
        section = "Rulers",
        title = "Reign years of the Samanid amirs Nasr II and Nuh I, summed and ranked",
        originalTitle = "Samanid amirs Nasr II and Nuh I",
        author = "Nasr II and Nuh I of the Samanids",
        year = 943,
        summary = "A short program works out the reign years of the Samanid amirs Nasr II and Nuh I, sums them to 40 and ranks the two reigns.",
        details = "Nasr II is given the years 914 to 943 and Nuh I the years 943 to 954, and the program subtracts each start year from its end year. The usual dates for these amirs are used, and the program ranks the reigns so that the longer one, Nasr II's, comes first.",
        rare = true,
        instructions = """
            1. Save the example as samanid_reigns.py.
            2. Run it: python3 samanid_reigns.py
        """,
        example = """
            reigns = [("Nasr II", 914, 943), ("Nuh I", 943, 954)]
            lengths = []
            for name, start, end in reigns:
                years = end - start
                lengths.append((years, name))
                print(name, "reigned", years, "years")
            print("total years:", sum(years for years, _ in lengths))
            longest = max(lengths)
            print("longest:", longest[1], longest[0], "years")
        """,
    ),

)
