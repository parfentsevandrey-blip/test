package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 1940 to 1950: a test that stops early, a signed multiplication, and a length rule for prefix codes. */
val db10Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 10,
        kind = Kind.FLEX,
        section = "Statistics",
        title = "Wald's sequential test stops early",
        originalTitle = "Sequential tests of statistical hypotheses (Wald, 1945)",
        author = "Abraham Wald",
        year = 1945,
        summary = "A test that checks each observation as it arrives can stop after a few flips when the evidence is clear, instead of fixing the sample size in advance.",
        details = "The example runs the sequential probability ratio test on coin flips, with error rates of 10 per cent, and prints where each run stops. Wald's 1945 paper set out the method, which came out of the wartime statistical work at Columbia.",
        instructions = """
            1. Save the example as sprt.py.
            2. Run it: python3 sprt.py
        """,
        example = """
            from math import log

            p0, p1 = 0.5, 0.75                  # the coin's chance of heads under each hypothesis
            alpha = beta = 0.1                  # the two error rates
            upper = log((1 - beta) / alpha)     # accept the second hypothesis at or above this
            lower = log(beta / (1 - alpha))     # accept the first hypothesis at or below this

            def sprt(flips):
                total = 0.0
                for n, flip in enumerate(flips, start=1):
                    total += log(p1 / p0) if flip == "H" else log((1 - p1) / (1 - p0))
                    if total >= upper:
                        return f"decide H1 after {n} flips"
                    if total <= lower:
                        return f"decide H0 after {n} flips"
                return "no decision yet"

            print(sprt("HHHHHH"))
            print(sprt("TTTT"))
            print(sprt("HTHTHT"))
        """,
    ),

    entry(
        databaseId = 10,
        kind = Kind.FLEX,
        section = "Arithmetic",
        title = "Booth's signed multiplication",
        originalTitle = "A signed binary multiplication technique (Booth, 1951)",
        author = "Andrew Donald Booth",
        year = 1950,
        summary = "Booth's rule multiplies two's-complement numbers by adding or subtracting shifted copies of one operand, so negative numbers need no correction step.",
        details = "Booth devised the method in 1950 and published it in 1951. The example multiplies two signed 8-bit numbers with the rule, prints two sample products, and checks all 65536 pairs of operands against ordinary multiplication.",
        instructions = """
            1. Save the example as booth.py.
            2. Run it: python3 booth.py
        """,
        example = """
            def booth(m, r, bits=8):
                # The register has two extra bits, so that the negated multiplicand fits.
                # Each pair of multiplier bits decides whether to add or subtract it, then the register shifts right.
                width = 2 * bits + 2
                full = (1 << width) - 1
                mask = (1 << bits) - 1
                A = (m << (bits + 1)) & full
                S = ((-m) << (bits + 1)) & full
                P = (r & mask) << 1
                for _ in range(bits):
                    pair = P & 3
                    if pair == 1:
                        P = (P + A) & full
                    elif pair == 2:
                        P = (P + S) & full
                    sign = P >> (width - 1)
                    P = (P >> 1) | (sign << (width - 1))
                product = (P >> 1) & ((1 << (2 * bits + 1)) - 1)
                if product >= 1 << (2 * bits):
                    product -= 1 << (2 * bits + 1)
                return product

            print("-5 * 3 =", booth(-5, 3))
            print("7 * -8 =", booth(7, -8))
            ok = all(booth(a, b) == a * b for a in range(-128, 128) for b in range(-128, 128))
            print("all 65536 products match a * b:", ok)
        """,
    ),

    entry(
        databaseId = 10,
        kind = Kind.FLEX,
        section = "Coding",
        title = "Kraft's inequality for prefix codes",
        originalTitle = "A device for quantizing, grouping and coding amplitude-modulated pulses (Kraft, 1949)",
        author = "Leon G. Kraft",
        year = 1949,
        summary = "A list of codeword lengths can be the lengths of a prefix code exactly when the sum of two to the power minus each length is at most one.",
        details = "The example checks the sum for two lists of lengths, builds a prefix code for the list that passes, and reports that the other list cannot be a prefix code. The result is from Kraft's 1949 MIT thesis. Sources disagree on the degree, so the year alone is given here.",
        instructions = """
            1. Save the example as kraft.py.
            2. Run it: python3 kraft.py
        """,
        example = """
            from fractions import Fraction

            def kraft_sum(lengths):
                return sum(Fraction(1, 2 ** length) for length in lengths)

            def prefix_code(lengths):
                # Assign codewords in order of increasing length, counting upwards in binary.
                if kraft_sum(lengths) > 1:
                    return None
                codes, value, previous = [], 0, 0
                for length in sorted(lengths):
                    value <<= length - previous
                    codes.append(format(value, f"0{length}b"))
                    value += 1
                    previous = length
                return codes

            print("Kraft sum:", kraft_sum([1, 2, 3, 3]))
            print("codewords:", prefix_code([1, 2, 3, 3]))
            print("Kraft sum:", kraft_sum([1, 1, 2]), "prefix code:", prefix_code([1, 1, 2]))
        """,
        rare = true,
    ),
)
