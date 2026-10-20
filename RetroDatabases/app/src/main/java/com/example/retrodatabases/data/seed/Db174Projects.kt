package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 300 to 310: four dated turns of the war of the Eight Princes in Western Jin China, sorted and spaced in code. */
val db174Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 174,
        kind = Kind.PROJECT,
        section = "Timeline",
        title = "Four dated turns of the Eight Princes war in Western Jin China, 300 to 306, sorted in code",
        originalTitle = "Ba wang zhi luan",
        author = "The princes of the Western Jin court",
        year = 300,
        summary = "The war of the Eight Princes in Western Jin China is traced through four dated turns from 300 to 306, and a short program sorts them and prints the gaps between them.",
        details = "The war is usually dated from 291 to 306, but accounts differ on its span, and the four turns used here are the removal of Empress Jia Nanfeng in 300, the seizure and death of Sima Lun in 301, the Xiongnu leader Liu Yuan's title of Prince of Han in 304, and the deaths of Sima Ying and Emperor Hui in 306. The example sorts the four years and prints the gaps and the span, so each can be checked by hand.",
        instructions = """
            1. Save the example as war_timeline.py.
            2. Run it: python3 war_timeline.py
        """,
        example = """
            EVENTS = [
                (300, "Empress Jia Nanfeng removed"),
                (301, "Sima Lun seizes the throne and dies"),
                (304, "Liu Yuan takes the title Prince of Han"),
                (306, "Sima Ying killed and Emperor Hui dies"),
            ]
            events = sorted(EVENTS)
            for year, what in events:
                print(year, what)
            for (first, _), (second, _) in zip(events, events[1:]):
                print("gap", second - first, "years")
            print("span", events[-1][0] - events[0][0], "years")
        """,
        rare = true,
    ),

)
