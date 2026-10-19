package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One project of 750 to 760: three rulers who came to the throne between 754 and 757, their years in power summed and ranked by a short program. */
val db129Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 129,
        kind = Kind.PROJECT,
        section = "Rulers",
        title = "Three rulers who came to the throne between 754 and 757, ranked by the length of their rule",
        originalTitle = "Years in power",
        author = "Three rulers of the 750s",
        year = 754,
        summary = "Al-Mansur, Suzong and Offa came to power between 754 and 757, and their reigns total 66 years, which a short program sums and ranks.",
        details = "The years run from the usual date of each accession to the usual date of each death, so al-Mansur's span runs from 754 to 775, Suzong's from 756 to 762 and Offa's from 757 to 796. The program ranks the three by length, and the total shows only the arithmetic of the dates.",
        instructions = """
            1. Save the example as rulers_years.py.
            2. Run it: python3 rulers_years.py
        """,
        example = """
            RULERS = [
                ("al-Mansur, Abbasid caliph", 754, 775),
                ("Suzong, emperor of the Tang", 756, 762),
                ("Offa, king of Mercia", 757, 796),
            ]
            spans = {name: end - start for name, start, end in RULERS}
            for name, span in sorted(spans.items(), key=lambda item: -item[1]):
                print(name, span, "years")
            print("total:", sum(spans.values()), "years")
        """,
    ),

)
