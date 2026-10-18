package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 1180 to 1184: a seven-year sabbatical count, reckoning on lines with counters, and a cube root found digit by digit. */
val db86Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 86,
        kind = Kind.GUIDE,
        section = "Law",
        title = "How a seven-year cycle marks the sabbatical years of the Jewish law",
        originalTitle = "Shemitah",
        author = "Jewish law, as set out by Maimonides",
        year = 1180,
        summary = "A seven-year cycle marks every seventh year as a sabbatical, and a short program counts the sabbatical years of an illustrative cycle that starts in 1180.",
        details = "Maimonides's law code, completed about 1180, sets out the sabbatical and jubilee rules, and the example uses an illustrative cycle rather than the real Jewish reckoning of years. The output lists the years from 1180 to 1190 that fall on the cycle, so it shows the counting rule and not the historical sabbaticals.",
        instructions = """
            1. Save the example as sabbatical.py.
            2. Run it: python3 sabbatical.py
        """,
        example = """
            START = 1180
            CYCLE = 7
            for year in range(1180, 1191):
                if (year - START) % CYCLE == 0:
                    print(year, "sabbatical year")
            print("cycle length:", CYCLE)
        """,
    ),

    entry(
        databaseId = 86,
        kind = Kind.GUIDE,
        section = "Reckoning",
        title = "How two sums are added by carrying counters on the lines of an abacus",
        originalTitle = "Reckoning on lines",
        author = "Schoolmasters of the Latin west",
        year = 1184,
        summary = "Reckoning on lines with counters, the school method of the Latin west, adds two numbers by moving counters up whenever a line holds ten or more, and a short program follows each carry.",
        details = "Ten counters on one line were worth one counter on the line above, so each sum was carried upward line by line. The example adds 1186 and 2345, and each carry can be checked by hand against the totals that the program prints.",
        instructions = """
            1. Save the example as abacus.py.
            2. Run it: python3 abacus.py
        """,
        example = """
            a = [6, 8, 1, 1]   # units first, for 1186
            b = [5, 4, 3, 2]   # units first, for 2345
            names = ["units", "tens", "hundreds", "thousands"]
            lines = [a[i] + b[i] for i in range(4)]
            carry = 0
            for i in range(4):
                total = lines[i] + carry
                carry = total // 10
                left = total % 10
                print(names[i], total, "left", left, "carry", carry)
                lines[i] = left
            result = sum(d * 10 ** i for i, d in enumerate(lines))
            print("sum", result)
        """,
    ),

    entry(
        databaseId = 86,
        kind = Kind.GUIDE,
        section = "Roots",
        title = "How a cube root is found digit by digit",
        originalTitle = "Root extraction by digits",
        author = "Arabic arithmetic writers",
        year = 1182,
        summary = "The cube root of a number is found by setting its digits one place at a time, each the largest digit that keeps the cube within the number, and a short program follows the steps for 1185.",
        details = "Arabic arithmetic writers gave rules for roots of higher degree long before the 1180s, and testing one digit at a time is the rule the example follows. The program finds the root 10 of 1185 by testing the tens digit and then the units digit, and it prints the remainder left after the cube of the root is taken away.",
        instructions = """
            1. Save the example as cube_root.py.
            2. Run it: python3 cube_root.py
        """,
        example = """
            n = 1185
            root = 0
            for place in [10, 1]:
                digit = 0
                while (root + (digit + 1) * place) ** 3 <= n:
                    digit += 1
                root += digit * place
            print("cube root of", n, "is", root, "remainder", n - root ** 3)
            print("check", root ** 3 + (n - root ** 3))
        """,
    ),

)
