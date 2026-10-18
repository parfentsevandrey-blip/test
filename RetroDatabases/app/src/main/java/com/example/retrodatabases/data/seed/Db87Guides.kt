package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 1175: the whole-sign houses of a horoscope, set out from the rising sign in a short program. */
val db87Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 87,
        kind = Kind.GUIDE,
        section = "Astrology",
        title = "How the twelve whole-sign houses of a horoscope follow the rising sign",
        originalTitle = "Whole-sign houses",
        author = "Astrologers of the Arabic and Latin worlds",
        year = 1175,
        summary = "In the whole-sign method each of the twelve houses of a horoscope is one sign long, and a short program names the sign of each house from the rising sign.",
        details = "The first house is the sign that rises in the east, and each later house is the next sign in the order of the zodiac, so the twelve houses cover the whole circle once. The example uses an illustrative rising sign, Capricorn, and prints the sign of each house.",
        instructions = """
            1. Save the example as houses.py.
            2. Run it: python3 houses.py
        """,
        example = """
            SIGNS = ["Aries", "Taurus", "Gemini", "Cancer", "Leo", "Virgo",
                     "Libra", "Scorpio", "Sagittarius", "Capricorn", "Aquarius", "Pisces"]
            RISING = 9   # illustrative: Capricorn (index 9) rises in the east
            for house in range(1, 13):
                print("house", house, SIGNS[(RISING + house - 1) % 12])
            print("signs in the chart:", len({SIGNS[(RISING + h) % 12] for h in range(12)}))
        """,
        rare = true,
    ),

)
