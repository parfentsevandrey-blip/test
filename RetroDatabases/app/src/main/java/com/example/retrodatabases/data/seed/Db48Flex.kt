package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 1560 to 1570: the keys of a thirteen-row cipher, two February dates of 1564 and the prime factors of three years. */
val db48Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 48,
        kind = Kind.FLEX,
        section = "Ciphers",
        title = "Keys of a thirteen-row cipher, lengths one to five",
        originalTitle = "De furtivis literarum notis",
        author = "Giambattista della Porta",
        year = 1563,
        summary = "A key of k letters, each choosing one of thirteen rows, gives 13 to the power k possible keys, and the flex counts them for five lengths.",
        details = "Porta's cipher book of 1563 uses rows chosen by key letters, and the count assumes that each key letter picks one of thirteen rows. The example prints the number of keys for lengths one to five, which passes 370,000 at length five.",
        instructions = """
            1. Save the example as cipher_keys.py.
            2. Run it: python3 cipher_keys.py
        """,
        example = """
            for length in range(1, 6):
                print(f"key length {length}: {13 ** length} keys")
        """,
    ),

    entry(
        databaseId = 48,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Days from Galileo's birth to Michelangelo's death",
        originalTitle = "Dates of birth and death in 1564",
        author = "Galileo Galilei and Michelangelo Buonarroti",
        year = 1564,
        summary = "The Julian dates of Galileo's birth and Michelangelo's death in February 1564 are three days apart, and a short program counts the days.",
        details = "Galileo was born at Pisa on 15 February 1564 and Michelangelo died at Rome on 18 February 1564, both dates as commonly given in the Julian calendar. Both dates belong to the same calendar, so counting the days between them needs no conversion.",
        instructions = """
            1. Save the example as galileo_days.py.
            2. Run it: python3 galileo_days.py
        """,
        example = """
            from datetime import date

            galileo_birth = date(1564, 2, 15)       # as commonly given, in the Julian calendar
            michelangelo_death = date(1564, 2, 18)  # as commonly given, in the Julian calendar
            print("days from Galileo's birth to Michelangelo's death:", (michelangelo_death - galileo_birth).days)
        """,
    ),

    entry(
        databaseId = 48,
        kind = Kind.FLEX,
        section = "Numbers",
        title = "Prime factors of three years of the decade",
        originalTitle = "Prime factors of the years 1561, 1564 and 1567",
        author = "Compilers of the batch",
        year = 1564,
        summary = "Every whole number above one has a single list of prime factors, and a short program finds that list for three years of the decade.",
        details = "The method is trial division: divide by the smallest factor until none is left, which is slow for large numbers but fine for years. The example factors 1561, 1564 and 1567, and each year is printed as a product of primes.",
        instructions = """
            1. Save the example as prime_years.py.
            2. Run it: python3 prime_years.py
        """,
        example = """
            def factors(n):
                # Trial division: divide by the smallest factor until none is left.
                found, d = [], 2
                while d * d <= n:
                    while n % d == 0:
                        found.append(d)
                        n //= d
                    d += 1
                if n > 1:
                    found.append(n)
                return found

            for year in (1561, 1564, 1567):
                print(f"{year} = {' x '.join(str(p) for p in factors(year))}")
        """,
    ),

)
