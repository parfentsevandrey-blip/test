package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One project of 520 to 530: three dated events of 526 to 530, from the Antioch earthquake to the battle of Dara, sorted in code. */
val db152Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 152,
        kind = Kind.PROJECT,
        section = "Byzantine",
        title = "The Antioch earthquake of 526, the death of Justin I in 527 and the battle of Dara in 530, sorted and spaced in code",
        originalTitle = "The Antioch earthquake of 526, the death of Justin I in 527 and the battle of Dara in 530, sorted and spaced in code",
        author = "Sixth-century chroniclers",
        year = 526,
        summary = "A short program puts three events of 526 to 530 from the eastern Roman world in date order and prints the years between them.",
        details = "The earthquake that destroyed Antioch is dated to 526, the emperor Justin I died in 527, and the Byzantine victory at Dara came in 530. The program only subtracts the dates, so the gaps measure the years between the events and say nothing about a link between them.",
        rare = true,
        instructions = """
            1. Save the example as dated_events.py.
            2. Run it: python3 dated_events.py
        """,
        example = """
            events = [
                (530, "battle of Dara"),
                (526, "Antioch earthquake"),
                (527, "death of Justin I"),
            ]
            events.sort()
            for year, name in events:
                print(year, name)
            for earlier, later in zip(events, events[1:]):
                print("years from", earlier[1], "to", later[1] + ":", later[0] - earlier[0])
            print("years from first to last:", events[-1][0] - events[0][0])
        """,
    ),

)
