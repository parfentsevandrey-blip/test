package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex projects of 1950 to 1960: showpieces that squeeze the most out of a few lines of code, from Mersenne primes to Gray codes. */
val db6Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 6,
        kind = Kind.FLEX,
        section = "Number theory",
        title = "Lucas–Lehmer test for Mersenne primes",
        originalTitle = "Lucas–Lehmer primality test (SWAC, 1952)",
        author = "Edouard Lucas, Derrick Henry Lehmer and the SWAC searches",
        year = 1952,
        summary = "A short test for whether 2 to the power p minus 1 is prime. The SWAC used it in January 1952 to find new Mersenne primes.",
        details = "The test squares a number again and again, reducing it modulo the Mersenne number each time. Lucas devised the test in 1878, and D. H. Lehmer put it into its modern form in 1930. " +
            "The example checks the small exponents and then 521, which the SWAC found to be prime.",
        instructions = """
            1. Save the example as lucas.py.
            2. Run it: python3 lucas.py
        """,
        example = """
            def is_mersenne_prime(p):
                # Lucas-Lehmer test: 2**p - 1 is prime exactly when the last term is zero.
                if p == 2:
                    return True
                m = (1 << p) - 1
                s = 4
                for _ in range(p - 2):
                    s = (s * s - 2) % m
                return s == 0

            print([p for p in [2, 3, 5, 7, 11, 13, 17, 19, 23, 29, 31] if is_mersenne_prime(p)])
            print(is_mersenne_prime(521))
        """,
        rare = true,
    ),

    entry(
        databaseId = 6,
        kind = Kind.FLEX,
        section = "Codes",
        title = "Gray code: one bit changes per step",
        originalTitle = "Reflected binary code (Gray code)",
        author = "Frank Gray (patent granted 1953)",
        year = 1953,
        summary = "The reflected binary code, in which each step changes exactly one bit. Frank Gray's patent on it was granted in 1953.",
        details = "The code exists so that a reading taken while several bits are changing cannot be far off, which is why it was used for shaft encoders and pulse signals. The example prints the three-bit version as eight words.",
        instructions = """
            1. Save the example as gray.py.
            2. Run it: python3 gray.py
        """,
        example = """
            print(" ".join(format(n ^ (n >> 1), "03b") for n in range(8)))
        """,
    ),

    entry(
        databaseId = 6,
        kind = Kind.FLEX,
        section = "Random numbers",
        title = "Random numbers from a multiplicative generator",
        originalTitle = "Multiplicative congruential generator (Lehmer's form)",
        author = "D. H. Lehmer (1951 form); the constants are from 1988",
        year = 1951,
        summary = "One multiplication and one remainder give the next number in a sequence that looks random. Lehmer studied this form in 1951.",
        details = "The constants in the example, 16807 and 2 to the power 31 minus 1, come from a later choice made in 1988, not from Lehmer's own work. The sequence is completely fixed by its seed, so it can be repeated exactly.",
        instructions = """
            1. Save the example as random_values.py.
            2. Run it: python3 random_values.py
        """,
        example = """
            # Each number is the previous one times 16807, reduced modulo 2**31 - 1.
            seed = 1
            values = []
            for _ in range(5):
                seed = (16807 * seed) % 2147483647
                values.append(seed)
            print(*values)
        """,
    ),
)
