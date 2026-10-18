package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 960 to 970: the sign, degree and minute form of ecliptic longitude, written as a short program. */
val db108Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 108,
        kind = Kind.GUIDE,
        section = "Astronomy",
        title = "How a star's ecliptic longitude is written as a sign, a degree and a minute",
        originalTitle = "How a star's ecliptic longitude is written as a sign, a degree and a minute",
        author = "Ptolemy, kept in the star list of al-Sufi",
        year = 964,
        summary = "The sign, degree and minute form of ecliptic longitude used in star lists can be worked out in a few lines of code.",
        details = "Ptolemy's star list gives each position as a degree and minute inside one of the twelve signs of the zodiac, and al-Sufi's book of 964 keeps that style while revising the positions. The method is older than this decade, so the year 964 is the year of al-Sufi's book where it appears in this form, not the year of the method itself. The code counts minutes from the first point of Aries, divides by the thirty-degree width of a sign, and prints the sign with what is left over.",
        instructions = """
            1. Save the example as signs.py.
            2. Run it: python3 signs.py
        """,
        example = """
            # Ecliptic longitude in the sign, degree and minute form, counted from the first point of Aries.
            SIGNS = ["Aries", "Taurus", "Gemini", "Cancer", "Leo", "Virgo",
                     "Libra", "Scorpio", "Sagittarius", "Capricorn", "Aquarius", "Pisces"]

            def sign_form(degrees, minutes):
                total = degrees * 60 + minutes
                sign, rest = divmod(total, 30 * 60)
                return f"{SIGNS[sign]} {rest // 60} deg {rest % 60} min"

            for degrees, minutes in [(0, 0), (31, 35), (95, 15), (359, 59)]:
                print(f"{degrees} deg {minutes} min -> {sign_form(degrees, minutes)}")
        """,
    ),

)
