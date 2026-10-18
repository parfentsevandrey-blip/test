package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 1225 and 1226: a square five above and five below from Fibonacci's book of squares, and the weekday of Francis of Assisi's death in the Julian reckoning. */
val db82Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 82,
        kind = Kind.EXAMPLE,
        section = "Squares",
        title = "Checking a square that stays square five above and five below",
        originalTitle = "Congruum problem",
        author = "Leonardo Fibonacci, with a worked check",
        year = 1225,
        summary = "Fibonacci's Liber quadratorum poses a square that leaves squares when five is added and when five is taken away, and a short program checks the worked answer 41/12 in whole numbers.",
        details = "Scaling by 12 turns the square of 41/12 into 1681 over 144, and adding or taking away five turns it into 2401 and 961 over 144, which are the squares of 49 and 31. The integer check uses an exact square root, so the output does not depend on rounding.",
        instructions = """
            1. Save the example as congruum.py.
            2. Run it: python3 congruum.py
        """,
        example = """
            from math import isqrt

            def square_root(n):
                r = isqrt(n)
                return r if r * r == n else None

            n = 1681              # (41/12) squared, scaled by 144
            step = 5 * 144        # five units, scaled by 144
            up = square_root(n + step)
            down = square_root(n - step)
            print("plus five:", n + step, "is square of", up)
            print("minus five:", n - step, "is square of", down)
            print("both squares:", up is not None and down is not None)
        """,
    ),

    entry(
        databaseId = 82,
        kind = Kind.EXAMPLE,
        section = "Calendar",
        title = "The weekday of Francis of Assisi's death, counted from a Julian day number",
        originalTitle = "Julian calendar, 3 October 1226",
        author = "Franciscan chroniclers",
        year = 1226,
        summary = "Francis of Assisi died on 3 October 1226 in the Julian reckoning, and a short program turns that date into a Julian day number and then into a weekday.",
        details = "The usual account places his death on a Saturday, and the program confirms the weekday with a standard formula for the Julian calendar. The day number counts days from a fixed origin, so the same formula works for any date in the Julian reckoning.",
        instructions = """
            1. Save the example as weekday.py.
            2. Run it: python3 weekday.py
        """,
        example = """
            def julian_day_number(year, month, day):
                a = (14 - month) // 12
                y = year + 4800 - a
                m = month + 12 * a - 3
                return day + (153 * m + 2) // 5 + 365 * y + y // 4 - 32083

            WEEKDAYS = ["Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"]
            jdn = julian_day_number(1226, 10, 3)
            print("julian day number:", jdn)
            print("weekday:", WEEKDAYS[(jdn + 1) % 7])
        """,
    ),

)
