package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 1500 to 1510: Ash Wednesday dates, the days from Cabral's landing to 1501, and the perfect numbers in Euclid's ninth book. */
val db54Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 54,
        kind = Kind.FLEX,
        section = "Church calendar",
        title = "Ash Wednesday dates for 1500, 1505 and 1510",
        originalTitle = "Ash Wednesday, forty-six days before Easter",
        author = "Church calendar",
        year = 1500,
        summary = "Ash Wednesday falls forty-six days before Easter, and a short program finds the Ash Wednesday of three years of the decade from their Easter dates.",
        details = "The forty-six days of Lent are counted back from each Easter Sunday, and the Easter dates used here are the ones the Julian reckoning gives for those years. The example uses fixed dates, so the output never changes.",
        instructions = """
            1. Save the example as ash_wednesday.py.
            2. Run it: python3 ash_wednesday.py
        """,
        example = """
            from datetime import date, timedelta

            MONTHS = ["January", "February", "March", "April", "May", "June",
                      "July", "August", "September", "October", "November", "December"]
            # Easter dates in the Julian reckoning, fixed here so that the example stands alone.
            easters = {1500: date(1500, 4, 19), 1505: date(1505, 3, 23), 1510: date(1510, 3, 31)}
            for year, easter in easters.items():
                ash = easter - timedelta(days=46)
                print(f"{year}: {MONTHS[ash.month - 1]} {ash.day}")
        """,
    ),

    entry(
        databaseId = 54,
        kind = Kind.FLEX,
        section = "Voyages",
        title = "Days from Cabral's landing to the new year 1501",
        originalTitle = "Carta a El-Rei D. Manuel",
        author = "Pero Vaz de Caminha",
        year = 1500,
        summary = "Cabral's fleet reached the coast of Brazil on 22 April 1500, and a short program counts the days from that landing to 1 January 1501.",
        details = "Pero Vaz de Caminha's letter to King Manuel describes the coast that the fleet found, and the landing date of 22 April 1500 is the one commonly given. The example counts the days between two fixed dates, so the output never changes.",
        instructions = """
            1. Save the example as landing_days.py.
            2. Run it: python3 landing_days.py
        """,
        example = """
            from datetime import date

            landing = date(1500, 4, 22)   # the landing in Brazil, as commonly given
            new_year = date(1501, 1, 1)
            print(f"days from the landing to 1 January 1501: {(new_year - landing).days}")
        """,
    ),

    entry(
        databaseId = 54,
        kind = Kind.FLEX,
        section = "Arithmetic",
        title = "Perfect numbers in Euclid's ninth book",
        originalTitle = "Elements, book nine",
        author = "Euclid",
        year = 1500,
        summary = "Euclid's ninth book names the numbers that equal the sum of their proper divisors, and a short program finds the perfect numbers below 10000.",
        details = "Euclid's Elements, in book nine, shows how to build such numbers from powers of two whose sums are prime, and the perfect numbers below 10000 are 6, 28, 496 and 8128. The example checks every number below 10000 by summing its proper divisors, so the output is fixed.",
        instructions = """
            1. Save the example as perfect_numbers.py.
            2. Run it: python3 perfect_numbers.py
        """,
        example = """
            def proper_divisor_sum(number):
                # Sum of the divisors of a number below the number itself.
                total = 1 if number > 1 else 0
                divisor = 2
                while divisor * divisor <= number:
                    if number % divisor == 0:
                        total += divisor
                        if divisor * divisor != number:
                            total += number // divisor
                    divisor += 1
                return total

            for number in range(2, 10000):
                if proper_divisor_sum(number) == number:
                    print(number)
        """,
    ),

)
