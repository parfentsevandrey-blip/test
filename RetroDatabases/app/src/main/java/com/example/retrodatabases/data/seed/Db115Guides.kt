package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One guide of 890 to 900: the parallel organum of the Musica enchiriadis, written out as a short program that doubles a chant a fifth and a fourth below. */
val db115Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 115,
        kind = Kind.GUIDE,
        section = "Music",
        title = "How the Musica enchiriadis doubles a chant in parallel organum at the fifth and the fourth",
        originalTitle = "Musica enchiriadis",
        author = "Anonymous, sometimes linked with Hucbald of Saint-Amand",
        year = 900,
        summary = "A short program doubles a plainsong a fifth and a fourth below, which is the parallel organum taught in the Musica enchiriadis.",
        details = "The treatise, dated about 900 and written by an unknown author, describes organum in which a second voice keeps the chant's intervals at a fifth or a fourth below. The example uses modern pitch numbers, with C4 at 60, and prints the chant with its two organal voices for a short melody.",
        instructions = """
            1. Save the example as enchiriadis.py.
            2. Run it: python3 enchiriadis.py
        """,
        example = """
            chant = [60, 62, 64, 65, 67]
            fifth_below = [note - 7 for note in chant]
            fourth_below = [note - 5 for note in chant]
            print("chant:", chant)
            print("organum a fifth below:", fifth_below)
            print("organum a fourth below:", fourth_below)
            print("intervals kept:", all(c - f == 7 for c, f in zip(chant, fifth_below)))
        """,
        rare = true,
    ),

)
