package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 120 to 130: how seven eggs on the spina of a Roman circus counted the laps of a chariot race, placed at 125. */
val db192Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 192,
        kind = Kind.GUIDE,
        section = "Counting",
        title = "How seven eggs count the laps of a chariot race, placed at 125",
        originalTitle = "Ova",
        author = "Roman circus officials",
        year = 125,
        summary = "A Roman circus counted the seven laps of a chariot race with eggs on the spina, and a short program takes one egg down at each lap.",
        details = "Seven eggs and seven dolphins stood on the spina of a Roman circus to count the laps, and the example takes one egg down at each lap as a simple reading of the count. The sources do not date this count; 125 is the year of the anchor, the accession of Emperor Shun, which accounts give as 125 or 126, not of the counting.",
        instructions = """
            1. Save the example as eggs.py.
            2. Run it: python3 eggs.py
        """,
        example = """
            EGGS = 7   # seven laps, marked by seven eggs on the spina
            for lap in range(1, EGGS + 1):
                eggs_left = EGGS - lap
                print("lap", lap, "done, eggs left on the spina:", eggs_left)
            print("laps in the race:", EGGS)
        """,
        rare = true,
    ),

)
