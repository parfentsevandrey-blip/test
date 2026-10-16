package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 1314 to 1316: the days between the Council of Ten, the fight at Bannockburn and John XXII's election. */
val db73Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 73,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Days from the founding of Venice's Ten to John XXII's election",
        originalTitle = "Days between two dates",
        author = "No single author",
        year = 1316,
        summary = "The Council of Ten was set up on 10 July 1310 and John XXII was elected pope on 7 August 1316, and a short program counts the days between the two dates.",
        details = "Both dates are given as commonly stated, and the span includes the leap days of February 1312 and February 1316. The count is the same in the Julian reckoning, since no century year falls in the span.",
        instructions = """
            1. Save the example as venice_pope.py.
            2. Run it: python3 venice_pope.py
        """,
        example = """
            from datetime import date

            venice_ten = date(1310, 7, 10)    # the Council of Ten, as commonly dated
            john_xxii = date(1316, 8, 7)      # John XXII's election, as commonly dated
            print("days from the founding of Venice's Ten to John XXII's election:", (john_xxii - venice_ten).days)
        """,
    ),

    entry(
        databaseId = 73,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Days from the founding of Venice's Ten to the fight at Bannockburn",
        originalTitle = "Days between two dates",
        author = "No single author",
        year = 1314,
        summary = "The Council of Ten was set up on 10 July 1310 and the fight at Bannockburn is counted from 24 June 1314, and a short program counts the days between the two dates.",
        details = "Both dates are given as commonly stated, and the span includes the leap day of February 1312. The count is the same in the Julian reckoning, since no century year falls in the span, and the two parts of this count add up with the next piece in this series.",
        instructions = """
            1. Save the example as venice_bannockburn.py.
            2. Run it: python3 venice_bannockburn.py
        """,
        example = """
            from datetime import date

            venice_ten = date(1310, 7, 10)    # the Council of Ten, as commonly dated
            bannockburn = date(1314, 6, 24)   # the second day of the fighting at Bannockburn
            print("days from the founding of Venice's Ten to the fight at Bannockburn:", (bannockburn - venice_ten).days)
        """,
    ),

    entry(
        databaseId = 73,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Days from the fight at Bannockburn to John XXII's election",
        originalTitle = "Days between two dates",
        author = "No single author",
        year = 1316,
        summary = "The fight at Bannockburn is counted from 24 June 1314, its second day, to John XXII's election on 7 August 1316, and a short program counts the days between them.",
        details = "Both dates are given as commonly stated, and the span includes the leap day of February 1316. The two pieces of this series for the same span add up to the count from the founding of the Council of Ten.",
        instructions = """
            1. Save the example as bannockburn_pope.py.
            2. Run it: python3 bannockburn_pope.py
        """,
        example = """
            from datetime import date

            bannockburn = date(1314, 6, 24)   # the second day of the fighting at Bannockburn
            john_xxii = date(1316, 8, 7)      # John XXII's election, as commonly dated
            print("days from the fight at Bannockburn to John XXII's election:", (john_xxii - bannockburn).days)
        """,
    ),

)
