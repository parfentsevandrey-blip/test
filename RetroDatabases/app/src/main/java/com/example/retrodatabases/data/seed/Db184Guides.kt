package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 200 to 210: how the twenty-four runes of the Elder futhark fall into three aettir of eight, shown as a program. */
val db184Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 184,
        kind = Kind.GUIDE,
        section = "Runes",
        title = "How the twenty-four runes of the Elder futhark are grouped into three aettir of eight",
        originalTitle = "Elder futhark aettir",
        author = "Later Norse tradition",
        year = 200,
        summary = "The twenty-four signs of the Elder futhark fall into three aettir of eight, and a short program prints each set in order.",
        details = "The division is not shown in the earliest inscriptions, and later Norse tradition names the three sets after Freyr, Hagal and Tyr, so the year 200 is the anchor for the alphabet's earliest finds and not the date of the grouping. The example prints each set with its transliterated signs and counts the total.",
        instructions = """
            1. Save the example as runes.py.
            2. Run it: python3 runes.py
        """,
        example = """
            RUNES = [
                ("f", "fehu"), ("u", "uruz"), ("th", "thurisaz"), ("a", "ansuz"),
                ("r", "raidho"), ("k", "kaunan"), ("g", "gebo"), ("w", "wunjo"),
                ("h", "hagalaz"), ("n", "naudiz"), ("i", "isa"), ("j", "jera"),
                ("ei", "eihwaz"), ("p", "perthro"), ("z", "algiz"), ("s", "sowilo"),
                ("t", "tiwaz"), ("b", "berkano"), ("e", "ehwaz"), ("m", "mannaz"),
                ("l", "laguz"), ("ng", "ingwaz"), ("d", "dagaz"), ("o", "othala"),
            ]
            AETTIR = ["Freyr's aett", "Hagal's aett", "Tyr's aett"]
            total = 0
            for number, name in enumerate(AETTIR):
                group = RUNES[number * 8:(number + 1) * 8]
                total += len(group)
                print(name + ":", " ".join(sign for sign, _ in group))
            print("runes in the three sets:", total)
        """,
        rare = true,
    ),

)
