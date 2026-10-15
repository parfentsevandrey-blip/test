package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 1710 to 1720: copyright terms, quarantine dates, a note cover and the Spectator's numbers counted by day. */
val db33Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 33,
        kind = Kind.PROJECT,
        section = "Copyright",
        title = "Copyright terms under the Statute of Anne",
        originalTitle = "An Act for the Encouragement of Learning",
        author = "Parliament of Great Britain",
        year = 1710,
        summary = "The fourteen-year term with one renewal gives the year a book leaves copyright, and the program prints it for three publication years.",
        details = "A book was protected for fourteen years from publication, and for another fourteen if its author was living at the end of that time. The program takes books published in 1710, 1715 and 1720, assumes the authors were living, and prints the end of each term.",
        instructions = """
            1. Save the example as statute_terms.py.
            2. Run it: python3 statute_terms.py
        """,
        example = """
            FIRST_TERM = 14    # years from publication
            RENEWAL = 14       # further years if the author was still living at the end of the first term

            def expiry(publication_year, author_living):
                first_end = publication_year + FIRST_TERM
                if not author_living:
                    return first_end, first_end
                return first_end, first_end + RENEWAL

            for year in (1710, 1715, 1720):
                first_end, final_end = expiry(year, author_living=True)
                print(f"published {year}: first term ends {first_end}, with renewal ends {final_end}")
        """,
    ),

    entry(
        databaseId = 33,
        kind = Kind.PROJECT,
        section = "Public health",
        title = "Quarantine release dates at Marseille in 1720",
        originalTitle = "Cordon sanitaire of Provence",
        author = "Authorities of Provence and Marseille",
        year = 1720,
        summary = "A ship held for forty days of quarantine is released forty days after arrival, and the program computes the release date for three arrivals.",
        details = "Ships suspected of carrying plague were held for forty days before people and goods were released, a practice that gave the word quarantine. The example takes three illustrative arrival dates in May 1720 and prints each release date using the standard library's calendar arithmetic.",
        instructions = """
            1. Save the example as quarantine_dates.py.
            2. Run it: python3 quarantine_dates.py
        """,
        example = """
            from datetime import date, timedelta

            QUARANTINE_DAYS = 40

            for arrival in (date(1720, 5, 1), date(1720, 5, 20), date(1720, 5, 27)):
                release = arrival + timedelta(days=QUARANTINE_DAYS)
                print(f"arrived {arrival.isoformat()}: released {release.isoformat()}")
        """,
    ),

    entry(
        databaseId = 33,
        kind = Kind.PROJECT,
        section = "Banking",
        title = "Notes issued against a metal reserve",
        originalTitle = "Banque generale",
        author = "John Law",
        year = 1716,
        summary = "A bank that keeps a fixed coin reserve while its paper issue grows sees the coin cover for each note fall, which the program tracks with exact fractions.",
        details = "Law's Banque generale issued notes payable in coin from 1716, so the bank's safety depended on its reserve. The example uses illustrative figures, a reserve of 100 coins and issues of 40, 60, 90 and 120 notes, and prints the coin behind each note in each year.",
        instructions = """
            1. Save the example as note_cover.py.
            2. Run it: python3 note_cover.py
        """,
        example = """
            from fractions import Fraction as F

            RESERVE_COINS = 100                 # coins held against the notes, an illustrative figure
            issues = [40, 60, 90, 120]          # notes put into circulation in four successive years, illustrative

            notes = 0
            for year, issued in zip((1716, 1717, 1718, 1719), issues):
                notes += issued
                cover = F(RESERVE_COINS, notes)
                print(f"{year}: notes {notes}, coin per note {cover}")
        """,
    ),

    entry(
        databaseId = 33,
        kind = Kind.PROJECT,
        section = "Periodicals",
        title = "The Spectator's numbers counted by day",
        originalTitle = "The Spectator",
        author = "Joseph Addison and Richard Steele",
        year = 1711,
        summary = "The Spectator appeared on every day except Sunday from March 1711 to December 1712, and the program counts its numbers from the calendar.",
        details = "The essays ran from Thursday 1 March 1711 to Saturday 6 December 1712 in the Julian calendar, with no number on Sundays. The example counts the days in that span, skips the Sundays by weekday, and reports the 555 numbers that the count gives.",
        instructions = """
            1. Save the example as spectator_days.py.
            2. Run it: python3 spectator_days.py
        """,
        example = """
            from datetime import date, timedelta

            # Julian dates shifted eleven days to the Gregorian calendar, which keeps the weekdays right in 1711 and 1712.
            start = date(1711, 3, 12)    # 1 March 1711, Julian
            end = date(1712, 12, 17)     # 6 December 1712, Julian
            names = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"]

            days = [start + timedelta(days=k) for k in range((end - start).days + 1)]
            sundays = sum(1 for d in days if d.weekday() == 6)
            print(f"days from 1 March 1711 to 6 December 1712: {len(days)}")
            print(f"first day: {names[start.weekday()]}; last day: {names[end.weekday()]}")
            print(f"Sundays in the span: {sundays}")
            print(f"numbers published, one a day except Sundays: {len(days) - sundays}")
        """,
    ),
)
