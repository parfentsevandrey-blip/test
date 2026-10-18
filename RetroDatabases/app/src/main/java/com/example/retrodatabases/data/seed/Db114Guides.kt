package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One guide of 900 to 910, a placed record: Thabit ibn Qurra's rule for amicable pairs, placed at 901, the year of his death. */
val db114Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 114,
        kind = Kind.GUIDE,
        section = "Numbers",
        title = "How Thabit ibn Qurra's rule builds the amicable pair 220 and 284",
        originalTitle = "Thabit's rule for amicable numbers",
        author = "Thabit ibn Qurra",
        year = 901,
        summary = "Thabit ibn Qurra's rule builds amicable pairs from three prime numbers, and a short program checks that 220 and 284 form such a pair by adding up their divisors.",
        details = "This is a placed record: the year 901 is the death of Thabit ibn Qurra, the anchor used for placement, and it is not a date given for the rule itself. With n set to 2, the rule gives the primes 5, 11 and 71, which build the pair 2 squared times 5 times 11, that is 220, and 2 squared times 71, that is 284.",
        instructions = """
            1. Save the example as amicable.py.
            2. Run it: python3 amicable.py
        """,
        example = """
            def is_prime(n):
                if n < 2:
                    return False
                d = 2
                while d * d <= n:
                    if n % d == 0:
                        return False
                    d += 1
                return True

            def proper_divisor_sum(n):
                return sum(d for d in range(1, n) if n % d == 0)

            n = 2
            p = 3 * 2 ** (n - 1) - 1
            q = 3 * 2 ** n - 1
            r = 9 * 2 ** (2 * n - 1) - 1
            print("p, q, r:", p, q, r)
            print("all prime:", is_prime(p) and is_prime(q) and is_prime(r))
            a = 2 ** n * p * q
            b = 2 ** n * r
            print("pair:", a, b)
            print("divisor sum of", a, "is", proper_divisor_sum(a))
            print("divisor sum of", b, "is", proper_divisor_sum(b))
            print("amicable:", proper_divisor_sum(a) == b and proper_divisor_sum(b) == a)
        """,
    ),

)
