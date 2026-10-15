package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 1920 to 1930: an acceleration of a series, a matrix factor and a measure of information. */
val db12Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 12,
        kind = Kind.FLEX,
        section = "Series",
        title = "Aitken's delta-squared in a few lines",
        originalTitle = "On Bernoulli's numerical solution of algebraic equations (Aitken, 1926)",
        author = "Alexander Aitken",
        year = 1926,
        summary = "Three partial sums of a geometric series are combined into its exact limit, using the delta-squared correction that Aitken published in 1926.",
        details = "Aitken published the process in 1926, although one biography gives 1927, so the year is a choice. Some accounts trace the formula further back. The correction treats the sequence as geometric and removes the leading error term. The example uses the partial sums 1/2, 3/4 and 7/8 of the series 1/2 + 1/4 + 1/8 + ..., and the correction returns the limit 1 exactly.",
        instructions = """
            1. Save the example as aitken.py.
            2. Run it: python3 aitken.py
        """,
        example = """
            from fractions import Fraction

            # Partial sums of 1/2 + 1/4 + 1/8 + ... are 1/2, 3/4 and 7/8, and they converge to 1.
            s = [Fraction(1, 2), Fraction(3, 4), Fraction(7, 8)]
            d1 = s[1] - s[0]
            d2 = s[2] - s[1]
            dd = d2 - d1
            accelerated = s[0] - d1 * d1 / dd
            print("partial sums:", " ".join(str(x) for x in s))
            print("Aitken estimate:", accelerated)
        """,
    ),

    entry(
        databaseId = 12,
        kind = Kind.FLEX,
        section = "Linear algebra",
        title = "Cholesky factorisation in a few lines",
        originalTitle = "Cholesky's method for least squares (devised 1910, printed 1924)",
        author = "Andre-Louis Cholesky, printed by Commandant Benoit",
        year = 1924,
        summary = "Factors a symmetric positive definite matrix into a lower triangle times its transpose, the method Cholesky devised around 1910 for surveying.",
        details = "Cholesky devised the method around 1910 for least-squares work in surveying. He was killed in 1918, and the first printed account appeared in 1924, by a colleague. A Swedish geodesist extended the technique in 1926. The example factors a 3 by 3 matrix and prints the rows of its lower triangle, whose product with the transpose returns the original matrix.",
        instructions = """
            1. Save the example as cholesky.py.
            2. Run it: python3 cholesky.py
        """,
        example = """
            import math

            # Cholesky factor of a symmetric positive definite matrix: A = L times the transpose of L, with L lower triangular.
            A = [[4.0, 12.0, -16.0], [12.0, 37.0, -43.0], [-16.0, -43.0, 98.0]]
            n = len(A)
            L = [[0.0] * n for _ in range(n)]
            for i in range(n):
                for j in range(i + 1):
                    s = sum(L[i][k] * L[j][k] for k in range(j))
                    if i == j:
                        L[i][j] = math.sqrt(A[i][i] - s)
                    else:
                        L[i][j] = (A[i][j] - s) / L[j][j]
            for row in L:
                print(" ".join(str(int(round(x))) for x in row))
        """,
    ),

    entry(
        databaseId = 12,
        kind = Kind.FLEX,
        section = "Information",
        title = "Hartley's measure of information in a few lines",
        originalTitle = "Transmission of Information (Hartley, 1928)",
        author = "Ralph Hartley",
        year = 1928,
        summary = "A message of n symbols chosen from s signals carries n times the base-2 logarithm of s bits, the measure Hartley proposed in 1928.",
        details = "Hartley's paper appeared in 1928 and counted the choices a sender could make, taking logarithms so that independent choices add. The example prints three measures: one letter of a 26-letter alphabet carries about 4.70 bits, three symbols from four signals carry 6 bits, and eight binary choices carry 8 bits. Shannon's theory of 1948 extended the idea to unequal probabilities.",
        instructions = """
            1. Save the example as hartley.py.
            2. Run it: python3 hartley.py
        """,
        example = """
            import math

            # Hartley's measure: a message of n symbols from s distinct signals carries n * log2(s) bits.
            def hartley_bits(n, s):
                return n * math.log2(s)

            print(f"one symbol from 26 letters: {hartley_bits(1, 26):.2f} bits")
            print(f"three symbols from 4 signals: {hartley_bits(3, 4):.2f} bits")
            print(f"eight binary choices: {hartley_bits(8, 2):.2f} bits")
        """,
    ),
)
