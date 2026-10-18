package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** A project of 951 to 959: three dated events from Germany and Byzantium, sorted and spaced by a short program. */
val db109Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 109,
        kind = Kind.PROJECT,
        section = "Dates",
        title = "Years between Otto I's marriage, the battle of the Lechfeld and a Byzantine death",
        originalTitle = "Years between Otto I's marriage, the battle of the Lechfeld and a Byzantine death",
        author = "Otto I and Constantine VII Porphyrogenitus",
        year = 951,
        summary = "Three dated events of 951 to 959, from Germany and Byzantium, are sorted by a short program that reports the gaps between them.",
        details = "The events are Otto I's marriage to Adelheid in 951, his victory over the Magyars at the Lechfeld in 955, and the death of Constantine VII in 959. The years are given as commonly stated, so the run tests the arithmetic rather than the events.",
        instructions = """
            1. Save the example as dated_events.py.
            2. Run it: python3 dated_events.py
        """,
        example = """
            # Three dated events of 951 to 959, listed out of order at first
            events = [
                (959, "death of the Byzantine emperor Constantine VII"),
                (951, "marriage of Otto I to Adelheid"),
                (955, "Otto I's victory over the Magyars at the Lechfeld"),
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
        """,
    ),

)
