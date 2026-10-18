package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 1250 to 1260: a timeline of dated events, a relay of courier horses and a council's votes. */
val db79Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 79,
        kind = Kind.PROJECT,
        section = "Timelines",
        title = "Sorting the dated events of the 1250s into a timeline",
        originalTitle = "Timeline of dated events",
        author = "No single author",
        year = 1250,
        summary = "A short program sorts six dated events of the 1250s by their dates and prints them in order.",
        details = "The dates are written as commonly given, and the program sorts them as text in the year-month-day form, which keeps them in date order. The example prints each date followed by its event and then the number of events.",
        instructions = """
            1. Save the example as timeline.py.
            2. Run it: python3 timeline.py
        """,
        example = """
            events = [
                ("1258-06-11", "Provisions of Oxford"),
                ("1260-09-03", "battle of Ain Jalut"),
                ("1250-12-13", "death of Frederick II"),
                ("1259-08-11", "death of Mongke Khan"),
                ("1254-12-07", "death of Innocent IV"),
                ("1251-07-01", "Mongke Khan enthroned"),
            ]
            for date, name in sorted(events):
                print(date, name)
            print("events:", len(events))
        """,
    ),

    entry(
        databaseId = 79,
        kind = Kind.PROJECT,
        section = "Post roads",
        title = "Horse changes on a courier route with stations in a row",
        originalTitle = "Horse changes",
        author = "Couriers of the Mongol post roads",
        year = 1255,
        summary = "A courier who changes horses at each station of a route needs one change fewer than the number of stations, and a short program counts them.",
        details = "The example lists five illustrative stations, and the courier takes a fresh horse at every station after the first. The counts are invented for the example and show only the arithmetic of the relay.",
        instructions = """
            1. Save the example as horse_changes.py.
            2. Run it: python3 horse_changes.py
        """,
        example = """
            stations = ["station 1", "station 2", "station 3", "station 4", "station 5"]
            print("stations:", len(stations))
            print("horse changes:", len(stations) - 1)
        """,
    ),

    entry(
        databaseId = 79,
        kind = Kind.PROJECT,
        section = "Councils",
        title = "Three illustrative votes of a council of fifteen",
        originalTitle = "Tally of votes",
        author = "The councillors named in the Provisions of Oxford",
        year = 1258,
        summary = "A short program tallies three illustrative votes of a council of fifteen and reports which motions reach the majority of eight.",
        details = "The votes are invented for the example, with nine, seven and eight councillors in favour, and the program prints each result. The counts show the majority rule, not the record of any real vote.",
        instructions = """
            1. Save the example as council_votes.py.
            2. Run it: python3 council_votes.py
        """,
        example = """
            COUNCIL = 15
            NEEDED = COUNCIL // 2 + 1
            votes = [9, 7, 8]   # illustrative numbers in favour
            carried = 0
            for number, in_favour in enumerate(votes, start=1):
                result = "carried" if in_favour >= NEEDED else "not carried"
                if in_favour >= NEEDED:
                    carried += 1
                print("vote", number, in_favour, "for", result)
            print("carried", carried, "of", len(votes))
        """,
    ),

)
