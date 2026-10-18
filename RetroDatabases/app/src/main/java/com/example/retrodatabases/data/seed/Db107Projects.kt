package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** A project of 971 to 979: four realms that the Song absorbed, sorted and spaced by a short program. */
val db107Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 107,
        kind = Kind.PROJECT,
        section = "Song conquests",
        title = "Four realms absorbed by the Song from 971 to 979, and the years between them",
        originalTitle = "Four realms absorbed by the Song from 971 to 979, and the years between them",
        author = "Song emperors Taizu and Taizong",
        year = 971,
        summary = "Four realms that the Song absorbed between 971 and 979 are sorted by a short program that reports the years between them.",
        details = "The events are the surrender of Southern Han in 971, the fall of the Southern Tang capital Jinling in 975, the surrender of the Wuyue lands in 978, and the fall of Northern Han in 979. The run tests the arithmetic rather than the events, which are given as commonly stated.",
        rare = true,
        instructions = """
            1. Save the example as song_realms.py.
            2. Run it: python3 song_realms.py
        """,
        example = """
            # Four realms absorbed by the Song, listed out of order at first
            realms = [
                (978, "Wuyue lands surrendered"),
                (971, "Southern Han surrendered"),
                (979, "Northern Han fell"),
                (975, "Southern Tang capital taken"),
            ]

            realms.sort()
            gaps = [later[0] - earlier[0] for earlier, later in zip(realms, realms[1:])]
            print("realms:", len(realms))
            print("first:", realms[0][0], realms[0][1])
            print("last:", realms[-1][0], realms[-1][1])
            print("span in years:", realms[-1][0] - realms[0][0])
            print("gaps:", gaps)
            print("largest gap:", max(gaps))
            print("smallest gap:", min(gaps))
            print("gaps of one year:", sum(1 for gap in gaps if gap == 1))
        """,
    ),

)
