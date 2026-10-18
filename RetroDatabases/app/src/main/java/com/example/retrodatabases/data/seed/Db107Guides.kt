package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** A guide of 977: how a day counted in 100 ke is read in hours, minutes and seconds, the count of the Song water clocks. */
val db107Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 107,
        kind = Kind.GUIDE,
        section = "Timekeeping",
        title = "How a Song day counted in 100 ke is read in hours and minutes",
        originalTitle = "Ke reckoning of the Song day",
        author = "Song timekeepers",
        year = 977,
        summary = "Each ke of a day counted in 100 ke lasts 864 seconds, so a reading in ke becomes hours, minutes and seconds by one multiplication and two divisions.",
        details = "The day was divided into 100 ke, so one ke is 14.4 minutes, and this count was kept through the water clocks of the period. Zhang Sixun's instrument of 977 or 979 had a water clock regulating its drip, but the ke count is the period's general reckoning, so the year given is that of the instrument and not of the method.",
        instructions = """
            1. Save the example as ke_clock.py.
            2. Run it: python3 ke_clock.py
        """,
        example = """
            def ke_to_clock(ke):
                # one day has 100 ke, and each ke lasts 864 seconds (14.4 minutes)
                seconds = ke * 864
                hours, rest = divmod(seconds, 3600)
                minutes, secs = divmod(rest, 60)
                return hours, minutes, secs


            for ke in [0, 25, 37, 50, 99, 100]:
                hours, minutes, secs = ke_to_clock(ke)
                print(f"{ke} ke = {hours} h {minutes} min {secs} s")
        """,
        rare = true,
    ),

)
