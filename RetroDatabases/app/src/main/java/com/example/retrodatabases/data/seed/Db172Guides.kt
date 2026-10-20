package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guide of 321, placed: how the seven days of the week take their names from the planets, beginning with Saturn's day. */
val db172Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 172,
        kind = Kind.GUIDE,
        section = "Week",
        title = "How the seven days of the week take their names from the planets, beginning with Saturn's day",
        originalTitle = "Dies Saturni, dies Solis, dies Lunae",
        author = "Roman and Greek astronomers and astrologers",
        year = 321,
        summary = "The seven days of the week take their names from the planets in a fixed order, and a short program names each day by the planet that rules its first hour.",
        details = "The planets rule the hours in the order Saturn, Jupiter, Mars, the Sun, Venus, Mercury and the Moon, and because a day has 24 hours, which leaves three over each time, every new day starts three places further along that order. The method is far older than 321, so the record is placed at 321, the year of Constantine's Sunday law, which names Sunday as the venerable day of the Sun.",
        instructions = """
            1. Save the example as planet_week.py.
            2. Run it: python3 planet_week.py
        """,
        example = """
            HOUR_ORDER = ["Saturn", "Jupiter", "Mars", "Sun", "Venus", "Mercury", "Moon"]
            DAY_NAMES = {"Saturn": "Saturday", "Sun": "Sunday", "Moon": "Monday", "Mars": "Tuesday",
                         "Mercury": "Wednesday", "Jupiter": "Thursday", "Venus": "Friday"}

            # A day has 24 hours, and 24 leaves three over when divided by seven,
            # so each new day starts three places further along the hour order.
            ruler = 0
            for day in range(7):
                planet = HOUR_ORDER[ruler]
                print(DAY_NAMES[planet], "is ruled by", planet)
                ruler = (ruler + 3) % 7
        """,
        rare = true,
    ),

)
