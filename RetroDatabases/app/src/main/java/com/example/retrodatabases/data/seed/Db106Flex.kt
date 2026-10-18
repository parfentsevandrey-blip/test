package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex arithmetic of 980 to 990: three one-line sums about dated events of the decade. */
val db106Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 106,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Years from the start of Theophanu's regency in 983 to Hugh Capet's election in 987",
        originalTitle = "Years from the start of Theophanu's regency in 983 to Hugh Capet's election in 987",
        author = "Theophanu and Hugh Capet",
        year = 987,
        summary = "A one-line program subtracts 983, the year Theophanu's regency began, from 987, the year Hugh Capet was chosen king, and prints the four years between.",
        details = "Otto II died at Rome in December 983, and his widow Theophanu began her regency that year. Hugh Capet was chosen king of the Franks in 987, so the gap in whole years is four.",
        instructions = """
            1. Save the example as flex.py.
            2. Run it: python3 flex.py
        """,
        example = """
            print("years from the start of Theophanu's regency to Hugh Capet's election:", 987 - 983)
        """,
    ),

    entry(
        databaseId = 106,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Years from Ibn Sahl's treatise of 984 to the founding of the Ming dynasty in 1368",
        originalTitle = "Years from Ibn Sahl's treatise of 984 to the founding of the Ming dynasty in 1368",
        author = "Ibn Sahl and the Ming founders",
        year = 984,
        summary = "A one-line program subtracts 984, the date of Ibn Sahl's treatise on burning mirrors, from 1368, the year the Ming dynasty was founded, and prints the gap.",
        details = "The treatise and the founding of the Ming dynasty are dated here in whole years, and the gap is 384 years. The sum uses whole years only.",
        instructions = """
            1. Save the example as flex.py.
            2. Run it: python3 flex.py
        """,
        example = """
            print("years from Ibn Sahl's treatise to the founding of the Ming dynasty:", 1368 - 984)
        """,
    ),

    entry(
        databaseId = 106,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Years from the Peace of God at Charroux in 989 to the end of Theophanu's regency in 991",
        originalTitle = "Years from the Peace of God at Charroux in 989 to the end of Theophanu's regency in 991",
        author = "Councils of western France and Theophanu",
        year = 989,
        summary = "A one-line program subtracts 989, the year of the Peace of God at Charroux, from 991, the year Theophanu's regency ended, and prints the gap.",
        details = "Both events are dated here in whole years, so the gap is two years. The sum does not depend on the months or days of either event.",
        instructions = """
            1. Save the example as flex.py.
            2. Run it: python3 flex.py
        """,
        example = """
            print("years from the Peace of God to the end of Theophanu's regency:", 991 - 989)
        """,
    ),

)
