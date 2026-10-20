package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 50 to 59: four dated events from Cologne to Agrippina, sorted and spaced in code. */
val db199Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 199,
        kind = Kind.PROJECT,
        section = "Timelines",
        title = "Four events of the 50s, from Cologne to Agrippina, sorted and spaced in code",
        originalTitle = "Event timeline",
        author = "Tacitus and Suetonius, the main Roman accounts",
        year = 50,
        summary = "Four events from 50 to 59 are sorted by year and printed with a bar of dashes showing the gap in years from the first, in a short program.",
        details = "The events are the founding of the colony at Cologne in 50, the capture of Caratacus in 51, the death of Claudius in 54 and the murder of Agrippina in 59, each as commonly given. The example prints the gap from the first event with dashes, so the spacing can be checked by hand.",
        instructions = """
            1. Save the example as timeline_50s.py.
            2. Run it: python3 timeline_50s.py
        """,
        example = """
            events = [
                (59, "Agrippina killed"),
                (50, "Cologne founded"),
                (54, "Claudius dies, Nero succeeds"),
                (51, "Caratacus captured"),
            ]
            first = min(year for year, _ in events)
            for year, name in sorted(events):
                gap = year - first
                print(f"{year} {'-' * gap}| {name}")
        """,
    ),

)
