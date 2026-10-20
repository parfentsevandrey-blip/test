package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 280 to 290: the six ten-day xun periods of the sixty-day cycle, placed at 280 by the Jin conquest of Wu. */
val db176Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 176,
        kind = Kind.GUIDE,
        section = "Calendar",
        title = "How the six ten-day xun periods of the Chinese calendar are named",
        originalTitle = "xun",
        author = "Chinese calendar makers",
        year = 280,
        summary = "The six ten-day xun periods of the Chinese sixty-day cycle take their names from the jia days that open them, and a short program prints the six names.",
        details = "The sixty-day cycle pairs the ten stems with the twelve branches, and each xun opens on a jia day, so the six names are jia-zi, jia-xu, jia-shen, jia-wu, jia-chen and jia-yin. The sources do not date the xun names to 280; 280 is the year of the anchor, the Jin conquest of Wu, and not the year of the method.",
        instructions = """
            1. Save the example as xun.py.
            2. Run it: python3 xun.py
        """,
        example = """
            STEMS = ["jia", "yi", "bing", "ding", "wu", "ji", "geng", "xin", "ren", "gui"]
            BRANCHES = ["zi", "chou", "yin", "mao", "chen", "si", "wu", "wei", "shen", "you", "xu", "hai"]

            # the sixty-day cycle pairs the stems with the branches; each xun of ten days opens on a jia day
            starts = list(range(0, 60, 10))
            for start in starts:
                name = STEMS[start % 10] + "-" + BRANCHES[start % 12]
                print("xun starting at day", start + 1, ":", name)
            print("xuns in the sixty-day cycle:", len(starts))
        """,
        rare = true,
    ),

)
