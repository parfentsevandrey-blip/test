package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One project of 620 to 630: three dated events of Arabia, sorted by a short program that counts the years between them. */
val db142Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 142,
        kind = Kind.PROJECT,
        section = "Arabic",
        title = "Three events of 624 to 630 in Arabia, the battle of Badr, the truce of Hudaybiyya and the taking of Mecca, sorted and spaced in code",
        originalTitle = "Three events of 624 to 630 in Arabia, the battle of Badr, the truce of Hudaybiyya and the taking of Mecca, sorted and spaced in code",
        author = "Ibn Ishaq and later biographers",
        year = 624,
        summary = "A short program sorts three events of Arabia from 624 to 630 and prints the years between the first and the last.",
        details = "Early Islamic biographies date the battle of Badr to 624, the truce of Hudaybiyya to 628 and the taking of Mecca to 630, in the Common Era reckoning used here. The program counts the gap from the first event to the last, so the result depends only on those three dates.",
        instructions = """
            1. Save the example as arabia_events.py.
            2. Run it: python3 arabia_events.py
        """,
        example = """
            EVENTS = [
                ("taking of Mecca", 630),
                ("battle of Badr", 624),
                ("truce of Hudaybiyya", 628),
            ]
            for name, year in sorted(EVENTS, key=lambda event: event[1]):
                print(year, name)
            first = min(year for _, year in EVENTS)
            last = max(year for _, year in EVENTS)
            print("years from Badr to Mecca:", last - first)
        """,
    ),

)
