package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 430 to 434: the seven purities of the Visuddhimagga, Attila's years as a span, and the zones of Macrobius that can be lived in. */
val db161Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 161,
        kind = Kind.EXAMPLE,
        section = "Purities",
        title = "The seven purities of the Visuddhimagga, numbered in order",
        originalTitle = "Visuddhimagga",
        author = "Buddhaghosa",
        year = 430,
        summary = "The Visuddhimagga arranges its outline around seven purities, and a short program lists them in order and counts them.",
        details = "The seven purities are purity of virtue, of mind, of view, by overcoming doubt, by knowledge of the path and not the path, by knowledge of the way, and by knowledge and vision. The program prints them in the order of the handbook, using English names.",
        instructions = """
            1. Save the example as purities.py.
            2. Run it: python3 purities.py
        """,
        example = """
            PURITIES = [
                "purity of virtue",
                "purity of mind",
                "purity of view",
                "purity by overcoming doubt",
                "purity by knowledge of the path and not the path",
                "purity by knowledge of the way",
                "purity by knowledge and vision",
            ]
            for number, name in enumerate(PURITIES, start=1):
                print(number, name)
            print("purities:", len(PURITIES))
        """,
    ),

    entry(
        databaseId = 161,
        kind = Kind.EXAMPLE,
        section = "Steppe",
        title = "The years from Attila's joint rule to his death, counted by a program",
        originalTitle = "Regnum Hunnorum",
        author = "Attila and Bleda",
        year = 434,
        summary = "Attila shared rule with his brother Bleda from 434 and died in 453, and a short program counts the years between the two dates.",
        details = "Only the two dates are used, so the program counts the span between the start of the joint rule and the death of Attila, not the length of his sole rule. The span is a plain subtraction, and the printed line names the two events.",
        instructions = """
            1. Save the example as attila_span.py.
            2. Run it: python3 attila_span.py
        """,
        example = """
            FIRST_YEAR = 434   # joint rule of the brothers Bleda and Attila, as commonly given
            DEATH_YEAR = 453   # death of Attila, as commonly given
            print("years from the joint rule to the death of Attila:", DEATH_YEAR - FIRST_YEAR)
        """,
    ),

    entry(
        databaseId = 161,
        kind = Kind.EXAMPLE,
        section = "Zones",
        title = "Which of Macrobius's five zones can be lived in, sorted by a program",
        originalTitle = "Commentarii in Somnium Scipionis",
        author = "Macrobius",
        year = 430,
        summary = "Macrobius holds that two of the five zones of the Earth can be lived in, and a short program lists the five zones and picks out the habitable ones.",
        details = "In his scheme the torrid zone and the two frigid zones cannot be lived in, while the two temperate zones are habitable, and by his account we live in the northern one. The program prints the zones as Macrobius names them, not as measured latitudes.",
        instructions = """
            1. Save the example as zones.py.
            2. Run it: python3 zones.py
        """,
        example = """
            ZONES = [
                ("north frigid", False),
                ("north temperate", True),
                ("torrid", False),
                ("south temperate", True),
                ("south frigid", False),
            ]
            habitable = [name for name, can_live in ZONES if can_live]
            print("zones:", len(ZONES))
            print("habitable zones:", len(habitable))
            for name in habitable:
                print("habitable:", name)
        """,
    ),

)
