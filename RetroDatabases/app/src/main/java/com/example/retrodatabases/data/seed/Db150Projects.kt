package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One project of 540 to 550: the reigns of Justinian I and Khosrow I, whose lengths and 34-year overlap are counted in code. */
val db150Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 150,
        kind = Kind.PROJECT,
        section = "Byzantine and Persian",
        title = "The reigns of Justinian I and Khosrow I of Persia, 527 to 579, counted and ranked in code",
        originalTitle = "Justinian I and Khosrow I",
        author = "Procopius and Persian chronicles",
        year = 540,
        summary = "The reigns of Justinian I and Khosrow I overlapped for 34 years, and a short program counts and ranks their lengths and the overlap.",
        details = "Justinian I ruled from 527 to 565 and Khosrow I from 531 to 579, and the wars between their states ran through the 540s, including the sack of Antioch in 540. The start and end years are the usual ones for both reigns, and the program only subtracts and compares them.",
        instructions = """
            1. Save the example as reign_spans.py.
            2. Run it: python3 reign_spans.py
        """,
        example = """
            REIGNS = {
                "Justinian I": (527, 565),
                "Khosrow I": (531, 579),
            }
            lengths = {name: end - start for name, (start, end) in REIGNS.items()}
            for name, length in sorted(lengths.items(), key=lambda item: item[1], reverse=True):
                print(name, length, "years")
            print("total:", sum(lengths.values()), "years")
            print("longest:", max(lengths, key=lengths.get))
            latest_start = max(start for start, _ in REIGNS.values())
            earliest_end = min(end for _, end in REIGNS.values())
            print("overlap:", earliest_end - latest_start, "years")
        """,
    ),

)
