package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 190 to 197: the years from the Muratorian fragment to Severus's Parthian campaign. */
val db185Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 185,
        kind = Kind.FLEX,
        section = "Years",
        title = "Years from the Muratorian fragment to Severus's Parthian campaign",
        originalTitle = "Years between two dated items",
        author = "No single author",
        year = 197,
        summary = "The Muratorian fragment of about 190 and the start of Severus's Parthian campaign in 197 are seven years apart, and a short program does the subtraction.",
        details = "Both years are the dates used in these records, and the fragment's year is a round date within the span of about 170 to about 200 that scholars give for it. The program prints the years between the two dates as written here, so the answer is only as exact as those dates.",
        instructions = """
            1. Save the example as fragment_campaign.py.
            2. Run it: python3 fragment_campaign.py
        """,
        example = """
            fragment_year = 190    # the Muratorian fragment, a round date used in this record
            campaign_year = 197    # the start of Severus's Parthian campaign
            print("years from the fragment to the campaign:", campaign_year - fragment_year)
        """,
    ),

)
