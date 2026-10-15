package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 1700 to 1710: calendar day numbers, a copyright term, a stepped multiplier and a dance phrase count. */
val db34Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 34,
        kind = Kind.PROJECT,
        section = "Calendars",
        title = "Julian and Gregorian day numbers around 1700",
        originalTitle = "Julian and Gregorian calendars",
        author = "Julius Caesar and Pope Gregory XIII",
        year = 1700,
        summary = "A program converts dates in the Julian and Gregorian calendars to day numbers and shows how the labels of the two calendars differed around 1700.",
        details = "The Protestant German states adopted the Gregorian count in 1700, and the switch ran from Julian 18 February to Gregorian 1 March. The example uses standard day-number formulas, and it checks the Julian one against a known date from the 1582 reform.",
        instructions = """
            1. Save the example as day_numbers.py.
            2. Run it: python3 day_numbers.py
        """,
        example = """
            def julian_day_number(year, month, day):
                # Julian calendar: the day number of a date counted in the Julian calendar.
                return 367 * year - (7 * (year + 5001 + (month - 9) // 7)) // 4 + (275 * month) // 9 + day + 1729777

            def gregorian_day_number(year, month, day):
                # Gregorian calendar: months are counted from March, so January and February belong to the year before.
                a = (14 - month) // 12
                y = year + 4800 - a
                m = month + 12 * a - 3
                return day + (153 * m + 2) // 5 + 365 * y + y // 4 - y // 100 + y // 400 - 32045

            print("check, Julian 1582-10-04:", julian_day_number(1582, 10, 4) == 2299160)
            j = julian_day_number(1700, 2, 18)
            g = gregorian_day_number(1700, 3, 1)
            print("Julian 1700-02-18 is day", j)
            print("Gregorian 1700-03-01 is day", g)
            print("the day after Julian 1700-02-18 is Gregorian 1700-03-01:", g == j + 1)
            print("Gregorian 1700-02-18 is day", gregorian_day_number(1700, 2, 18))
        """,
    ),

    entry(
        databaseId = 34,
        kind = Kind.PROJECT,
        section = "Navigation",
        title = "A dead-reckoning log for three legs",
        originalTitle = "Shkola matematicheskikh i navigatskikh nauk",
        author = "Peter the Great",
        year = 1701,
        summary = "A program adds three legs of a voyage, each given by a course and a distance, to find the north and east displacement of the ship.",
        details = "Navigators of the period found a ship's position by adding up the northward and eastward parts of each leg, a method taught in the navigation schools of the time. The legs in the example are invented, and the program resolves each course into its north and east parts before adding them.",
        instructions = """
            1. Save the example as dead_reckoning.py.
            2. Run it: python3 dead_reckoning.py
        """,
        example = """
            import math

            LEGS = [(0, 10), (90, 5), (45, 4)]   # (course in degrees from north, distance); invented legs

            north = 0.0
            east = 0.0
            for course, distance in LEGS:
                # A leg's northward part is distance times cos(course), and its eastward part is distance times sin(course).
                north += distance * math.cos(math.radians(course))
                east += distance * math.sin(math.radians(course))

            print(f"north {north:.2f}, east {east:.2f}")
        """,
    ),

    entry(
        databaseId = 34,
        kind = Kind.PROJECT,
        section = "Calculating machines",
        title = "A stepped-drum multiplier by repeated addition",
        originalTitle = "Brevis descriptio machinae arithmeticae",
        author = "Gottfried Wilhelm Leibniz",
        year = 1710,
        summary = "A program multiplies two numbers by adding the multiplicand once for each unit of each digit of the multiplier, shifting one place per digit as the stepped drum did.",
        details = "Leibniz's machine multiplied in this way, and the example counts the additions used, which grow with the digits of the multiplier. The program models the process, and it is not a copy of the machine.",
        instructions = """
            1. Save the example as stepped_multiply.py.
            2. Run it: python3 stepped_multiply.py
        """,
        example = """
            def multiply_by_steps(a, b):
                # Each digit of b is handled in turn: a is added once for each unit of that digit, shifted to the digit's place.
                total = 0
                additions = 0
                place = 1
                while b > 0:
                    digit = b % 10
                    for _ in range(digit):
                        total += a * place
                        additions += 1
                    b //= 10
                    place *= 10
                return total, additions

            for a, b in ((1234, 56), (999, 999), (7, 0)):
                total, additions = multiply_by_steps(a, b)
                print(f"{a} x {b} = {total}, additions: {additions}")
        """,
    ),

    entry(
        databaseId = 34,
        kind = Kind.PROJECT,
        section = "Dance",
        title = "A Feuillet-style count of beats in a dance phrase",
        originalTitle = "Choregraphie, ou l'art de decrire la danse",
        author = "Raoul-Auger Feuillet",
        year = 1700,
        summary = "A program adds the beats of a phrase of steps, each written with its count, and divides the total into bars of four beats.",
        details = "Feuillet's notation gave each step its count, so the length of a phrase could be checked against the bars of the music. The phrase in the example is invented for the purpose, and the program only adds the counts and divides them into bars.",
        instructions = """
            1. Save the example as beat_count.py.
            2. Run it: python3 beat_count.py
        """,
        example = """
            PHRASE = [("plie", 2), ("glissade", 1), ("balance", 4), ("assemble", 1)]   # each step with its count in beats (an invented phrase)

            total = sum(beats for step, beats in PHRASE)
            bars, rest = divmod(total, 4)
            print("total beats:", total)
            print("full bars of four beats:", bars)
            print("beats left over:", rest)
        """,
    ),

)
