package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 1271: the empty papal sees of 1268 to 1277, each vacancy counted in days from one pope's death to the next election. */
val db77Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 77,
        kind = Kind.PROJECT,
        section = "Papacy",
        title = "How long the papal see stood empty in the 1270s, vacancy by vacancy",
        originalTitle = "Papal vacancies",
        author = "The cardinals of the period",
        year = 1271,
        summary = "The papal see stood empty for long spells between 1268 and 1277, and a short program lists each vacancy in days from a pope's death to the next election.",
        details = "The dates are the ones commonly given in the papal chronologies, and each vacancy is counted from the death of one pope to the election of the next. The longest gap, from the death of Clement IV in 1268 to the election of Gregory X at Viterbo in 1271, ended only after nearly three years without a pope.",
        instructions = """
            1. Save the example as papal_vacancies.py.
            2. Run it: python3 papal_vacancies.py
        """,
        example = """
            from datetime import date

            # Each pope's death and the next election, as commonly given in the papal chronologies
            VACANCIES = [
                ("Clement IV died", date(1268, 11, 29), "Gregory X elected", date(1271, 9, 1)),
                ("Gregory X died", date(1276, 1, 10), "Innocent V elected", date(1276, 2, 21)),
                ("Innocent V died", date(1276, 6, 22), "Adrian V elected", date(1276, 7, 11)),
                ("Adrian V died", date(1276, 8, 18), "John XXI elected", date(1276, 9, 13)),
                ("John XXI died", date(1277, 5, 20), "Nicholas III elected", date(1277, 11, 25)),
            ]

            total = 0
            for died_label, died, elected_label, elected in VACANCIES:
                days = (elected - died).days
                total += days
                print(f"{died_label} {died.isoformat()}, {elected_label} {elected.isoformat()}: {days} days")
            longest = max((elected - died).days for _, died, _, elected in VACANCIES)
            print("longest vacancy:", longest, "days")
            print("total days empty:", total)
        """,
    ),

)
