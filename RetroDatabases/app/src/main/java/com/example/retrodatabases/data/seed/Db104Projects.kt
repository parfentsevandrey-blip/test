package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** A project of 1002 to 1010: five dated events from Spain, China, Egypt, India and the sky, sorted and spaced by a short program. */
val db104Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 104,
        kind = Kind.PROJECT,
        section = "Dates",
        title = "Dated events of 1002 to 1010, sorted and spaced by years",
        originalTitle = "Dated events of 1002 to 1010, sorted and spaced by years",
        author = "Various rulers and observers",
        year = 1002,
        summary = "Five dated events of 1002 to 1010, from Spain, China, Egypt, India and the sky, are sorted by a short program that reports the years between them.",
        details = "The events are the death of Almanzor at Medinaceli in 1002, the treaty of Shanyuan in 1005, the supernova SN 1006, the destruction of the Holy Sepulchre church ordered by al-Hakim in 1009, and the consecration of the Brihadeeswara temple at Thanjavur in 1010. The program sorts them, lists the gaps between neighbours and averages them, and the years are given as commonly stated, so the run tests the arithmetic rather than the events.",
        rare = true,
        instructions = """
            1. Save the example as dated_events.py.
            2. Run it: python3 dated_events.py
        """,
        example = """
            # Five dated events of 1002 to 1010, listed out of order at first
            events = [
                (1009, "Holy Sepulchre church destroyed"),
                (1002, "death of Almanzor at Medinaceli"),
                (1010, "Brihadeeswara temple consecrated"),
                (1006, "supernova SN 1006 seen"),
                (1005, "treaty of Shanyuan"),
            ]

            events.sort()
            gaps = [later[0] - earlier[0] for earlier, later in zip(events, events[1:])]
            print("events:", len(events))
            print("first:", events[0][0], events[0][1])
            print("last:", events[-1][0], events[-1][1])
            print("span in years:", events[-1][0] - events[0][0])
            print("gaps:", gaps)
            print("largest gap:", max(gaps))
            print("smallest gap:", min(gaps))
            print("average gap:", sum(gaps) / len(gaps))
            print("gaps of one year:", sum(1 for gap in gaps if gap == 1))
        """,
    ),

)
