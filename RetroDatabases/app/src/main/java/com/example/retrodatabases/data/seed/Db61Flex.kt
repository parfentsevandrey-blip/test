package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 1430 to 1440: the stem and branch names of the years, the happy years of the decade and the prime years. */
val db61Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 61,
        kind = Kind.FLEX,
        section = "Calendar",
        title = "The stem and branch names of the years 1430 to 1440",
        originalTitle = "Ganzhi",
        author = "The calendar makers of China",
        year = 1430,
        summary = "The years of the 1430s carry stem and branch names from a cycle of sixty, and a short program prints each year beside its name.",
        details = "The cycle pairs ten stems with twelve branches, so each pair recurs every sixty years. The example counts from year 4, when the cycle begins with jia-zi, and it prints the names of the years 1430 to 1440.",
        instructions = """
            1. Save the example as sexagenary.py.
            2. Run it: python3 sexagenary.py
        """,
        example = """
            STEMS = ["jia", "yi", "bing", "ding", "wu", "ji", "geng", "xin", "ren", "gui"]
            BRANCHES = ["zi", "chou", "yin", "mao", "chen", "si", "wu", "wei", "shen", "you", "xu", "hai"]

            for year in range(1430, 1441):
                # The sixty-year cycle begins at year 4 with jia-zi, so the position is the year minus 4 taken modulo 60.
                index = (year - 4) % 60
                print(year, STEMS[index % 10] + "-" + BRANCHES[index % 12])
        """,
    ),

    entry(
        databaseId = 61,
        kind = Kind.FLEX,
        section = "Digits",
        title = "Happy and unhappy years of the 1430s",
        originalTitle = "Happy numbers",
        author = "Compiled for this exercise",
        year = 1430,
        summary = "A number is happy when repeatedly replacing it by the sum of the squares of its digits reaches one, and a short program finds that none of the years of the 1430s is happy.",
        details = "The rule is a puzzle of digit arithmetic and carries no historical date, so the years serve only as inputs. The program follows each chain until it reaches one or repeats a value, and every chain for the decade ends in the cycle that holds 4 and 16, so the output is fixed.",
        instructions = """
            1. Save the example as happy_years.py.
            2. Run it: python3 happy_years.py
        """,
        example = """
            def digit_square_sum(n):
                return sum(int(d) ** 2 for d in str(n))

            happy = 0
            for year in range(1430, 1441):
                seen = set()
                n = year
                while n != 1 and n not in seen:
                    seen.add(n)
                    n = digit_square_sum(n)
                label = "happy" if n == 1 else "unhappy"
                happy += n == 1
                print(year, label)
            print("happy years:", happy)
        """,
    ),

    entry(
        databaseId = 61,
        kind = Kind.FLEX,
        section = "Numbers",
        title = "The prime years of the 1430s",
        originalTitle = "Prime numbers",
        author = "Compiled for this exercise",
        year = 1433,
        summary = "Trial division by smaller numbers shows which years of the 1430s are prime, and a short program lists them.",
        details = "A prime has no divisor other than one and itself, and trial division checks each year against the numbers up to its square root. The output for the decade is fixed, so the answer can be checked by hand.",
        instructions = """
            1. Save the example as prime_years.py.
            2. Run it: python3 prime_years.py
        """,
        example = """
            def is_prime(n):
                if n < 2:
                    return False
                divisor = 2
                while divisor * divisor <= n:
                    if n % divisor == 0:
                        return False
                    divisor += 1
                return True

            primes = [year for year in range(1430, 1441) if is_prime(year)]
            print("primes among 1430 to 1440:", " ".join(str(p) for p in primes))
            print("count:", len(primes))
        """,
    ),

)
