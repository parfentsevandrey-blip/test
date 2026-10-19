package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex piece of 780 to 790: the five years from Charlemagne's Admonitio generalis of 789 to the council of Frankfurt of 794. */
val db126Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 126,
        kind = Kind.FLEX,
        section = "Years",
        title = "Years from Charlemagne's Admonitio generalis in 789 to the council of Frankfurt in 794, which condemned the Nicaean decrees",
        originalTitle = "Years between two Frankish church acts",
        author = "Charlemagne",
        year = 789,
        summary = "Charlemagne's Admonitio generalis of 789 and the council of Frankfurt of 794 are five years apart, which a short program prints from the two dates.",
        details = "The Admonitio set out church reform in 789, and the council of Frankfurt condemned the Nicaean decrees on icons in 794, so the span compares two Frankish church acts. The program only subtracts the two usual dates, and it does not show that one act led to the other.",
        instructions = """
            1. Save the example as admonitio_frankfurt.py.
            2. Run it: python3 admonitio_frankfurt.py
        """,
        example = """
            ADMONITIO = 789  # Charlemagne's Admonitio generalis, as commonly dated
            FRANKFURT = 794  # the council of Frankfurt, as commonly dated
            print("years from the Admonitio generalis to the council of Frankfurt:", FRANKFURT - ADMONITIO)
        """,
    ),

)
