package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 994 and 996: al-Khujandi's tilt in decimal degrees and seconds of arc, and the years covered by Richer's history. */
val db105Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 105,
        kind = Kind.EXAMPLE,
        section = "Astronomy",
        title = "The tilt of the ecliptic read at Rayy in 994, in decimal degrees and in seconds of arc",
        originalTitle = "The tilt of the ecliptic read at Rayy in 994, in decimal degrees and in seconds of arc",
        author = "Abu Mahmud al-Khujandi",
        year = 994,
        summary = "The tilt of 23 degrees 32 minutes 19 seconds that al-Khujandi reported for 994 comes to 23.538611 decimal degrees, or 84739 seconds of arc.",
        details = "The conversion divides the minutes by 60 and the seconds by 3600, and the program also adds up the total in seconds of arc to confirm the arithmetic. Modern biographies judge the reading about two arc-minutes low, so the conversion is exact while the value it converts is not the true tilt.",
        rare = true,
        instructions = """
            1. Save the example as tilt_units.py.
            2. Run it: python3 tilt_units.py
        """,
        example = """
            # The tilt of the ecliptic read at Rayy in 994, written in degrees, minutes and seconds.
            deg, minutes, seconds = 23, 32, 19

            decimal = deg + minutes / 60 + seconds / 3600
            total_seconds = deg * 3600 + minutes * 60 + seconds

            print(f"{deg} degrees {minutes} minutes {seconds} seconds = {decimal:.6f} degrees")
            print("total seconds of arc:", total_seconds)
        """,
    ),

    entry(
        databaseId = 105,
        kind = Kind.EXAMPLE,
        section = "History",
        title = "The years covered by Richer's Historiae, from 888 to 998, counted",
        originalTitle = "The years covered by Richer's Historiae, from 888 to 998, counted",
        author = "Richer of Reims",
        year = 996,
        summary = "Richer's four books of history cover the years 888 to 998, which is 111 years when both end years are counted.",
        details = "The count includes the first and the last year, so 998 minus 888 plus 1 gives 111. Richer wrote the history at Reims around 996, the year of the record, while the count itself concerns only the span of years the history covers.",
        instructions = """
            1. Save the example as richer_years.py.
            2. Run it: python3 richer_years.py
        """,
        example = """
            # The years covered by Richer's history, counted inclusively.
            first_year = 888
            last_year = 998

            print("years covered by the Historiae:", last_year - first_year + 1)
            print("from", first_year, "to", last_year)
        """,
    ),

)
