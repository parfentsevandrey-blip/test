package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 380 to 390: how an Ambrosian hymn stanza is checked for eight syllables to a line. */
val db166Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 166,
        kind = Kind.GUIDE,
        section = "Verse",
        title = "How an Ambrosian hymn stanza is checked for four lines of eight syllables",
        originalTitle = "Iambic dimeter",
        author = "Ambrose of Milan",
        year = 386,
        summary = "Ambrose's hymns for the church of Milan are built from four-line stanzas of iambic dimeter, and a short program checks that each line has eight syllables.",
        details = "Augustine describes the introduction of hymn singing at Milan, which is usually dated to 386, and Ambrose's hymns are written in iambic dimeter with eight syllables to a line. The example checks the first stanza of the Advent hymn Veni redemptor gentium, traditionally attributed to Ambrose, with each line split into syllables by hand and counted by the program. The count follows from the text, so the output can be checked on paper.",
        instructions = """
            1. Save the example as ambrose_stanza.py.
            2. Run it: python3 ambrose_stanza.py
        """,
        example = """
            LINES = [
                ["ve", "ni", "re", "demp", "tor", "gen", "ti", "um"],
                ["os", "ten", "de", "par", "tum", "vir", "gi", "nis"],
                ["mi", "re", "tur", "om", "ne", "sae", "cu", "lum"],
                ["ta", "lis", "de", "cet", "par", "tus", "de", "um"],
            ]
            total = 0
            for number, syllables in enumerate(LINES, start=1):
                total += len(syllables)
                print(f"line {number}: {len(syllables)} syllables, {' '.join(syllables)}")
            print("stanza lines:", len(LINES))
            print("syllables in the stanza:", total)
        """,
        rare = true,
    ),

)
