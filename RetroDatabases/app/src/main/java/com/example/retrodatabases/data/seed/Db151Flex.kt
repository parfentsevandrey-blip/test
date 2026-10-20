package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One flex piece of 530 to 540: the years from the Nika riot of 532, which burned the old church of Holy Wisdom, to the dedication of Hagia Sophia in 537. */
val db151Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 151,
        kind = Kind.FLEX,
        section = "Byzantine",
        title = "Years from the Nika riot of 532, which burned the old church of Holy Wisdom, to the dedication of Hagia Sophia in 537",
        originalTitle = "Years from the Nika riot of 532, which burned the old church of Holy Wisdom, to the dedication of Hagia Sophia in 537",
        author = "Justinian's builders and the chroniclers of Constantinople",
        year = 532,
        summary = "The Nika riot of January 532 burned the old church on the site, and the rebuilt Hagia Sophia was dedicated five years later, a gap that a one-line program prints.",
        details = "Justinian rebuilt the church on the same site, and the new building was dedicated on 27 December 537, which is the end date of the gap. The five years are only the gap between the two dates, and they say nothing about the exact length of the building work, which began soon after the riot.",
        instructions = """
            1. Save the example as nika_hagia.py.
            2. Run it: python3 nika_hagia.py
        """,
        example = """
            NIKA_RIOT = 532   # the Nika riot in Constantinople, which burned the old church
            DEDICATION = 537  # the dedication of the rebuilt church, Hagia Sophia
            print("years from the Nika riot to the dedication of Hagia Sophia:", DEDICATION - NIKA_RIOT)
        """,
    ),

)
