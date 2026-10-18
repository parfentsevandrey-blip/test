package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 945: the 200 chapters of the Old Book of Tang added up by its three parts, annals, treatises and biographies. */
val db110Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 110,
        kind = Kind.EXAMPLE,
        section = "Histories",
        title = "The chapters of the Old Book of Tang added up by part: annals, treatises and biographies",
        originalTitle = "Jiu Tangshu",
        author = "Liu Xu and the compilers of the Later Jin court",
        year = 945,
        summary = "The Old Book of Tang's 200 chapters are split into 20 annals, 30 treatises and 150 biographies, and a short script adds the three parts back to the total.",
        details = "The 200 chapters are the usual figure for the work, and the three parts follow its arrangement into annals, treatises and biographies. The arithmetic is exact, so the program checks the sum rather than the history.",
        instructions = """
            1. Save the example as tang_chapters.py.
            2. Run it: python3 tang_chapters.py
        """,
        example = """
            annals = 20
            treatises = 30
            biographies = 150
            parts = {"annals": annals, "treatises": treatises, "biographies": biographies}
            for name, count in parts.items():
                print(name, count)
            print("chapters in all:", sum(parts.values()))
        """,
    ),

)
