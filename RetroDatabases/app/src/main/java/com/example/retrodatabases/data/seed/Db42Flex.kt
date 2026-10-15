package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 1620 to 1630: a critical angle for glass, the entries of Briggs's table and the gap that Vlacq filled. */
val db42Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 42,
        kind = Kind.FLEX,
        section = "Optics",
        title = "The critical angle of glass in one line",
        originalTitle = "Snell's manuscript notes on refraction",
        author = "Willebrord Snellius",
        year = 1621,
        summary = "Light passing from glass into air at angles above the critical angle is turned back inside the glass, and one line of code computes that angle for glass of index 1.5.",
        details = "The critical angle follows from Snell's law: the sine of the critical angle is one over the index. The example computes it for an index of 1.5 and prints it to two places.",
        instructions = """
            1. Save the example as critical_angle.py.
            2. Run it: python3 critical_angle.py
        """,
        example = """
            import math
            print(f"critical angle for glass of index 1.5: {math.degrees(math.asin(1 / 1.5)):.2f} degrees")
        """,
    ),

    entry(
        databaseId = 42,
        kind = Kind.FLEX,
        section = "Logarithms",
        title = "Entries in Briggs's table, counted",
        originalTitle = "Arithmetica logarithmica",
        author = "Henry Briggs",
        year = 1624,
        summary = "Briggs's table covers two ranges of whole numbers, and one line of code counts the entries in each range and in the whole table.",
        details = "Briggs's table gives logarithms for 1 to 20000 and for 90000 to 100000. The example counts the entries in each range, and the two ranges together make 30001 entries.",
        instructions = """
            1. Save the example as briggs_count.py.
            2. Run it: python3 briggs_count.py
        """,
        example = """
            RANGES = [(1, 20000), (90000, 100000)]
            counts = [hi - lo + 1 for lo, hi in RANGES]
            print("entries in each range:", counts)
            print("entries in the whole table:", sum(counts))
        """,
    ),

    entry(
        databaseId = 42,
        kind = Kind.FLEX,
        section = "Logarithms",
        title = "The gap that Vlacq filled, counted",
        originalTitle = "Arithmetica logarithmica",
        author = "Adriaan Vlacq",
        year = 1628,
        summary = "Vlacq's table covers every number up to 100000, and one line of code counts the numbers in the gap that Briggs left, from 20001 to 89999.",
        details = "Vlacq's Gouda printing of 1628 added the range from 20000 to 90000 that Briggs had not printed. The example counts the gap, checks it against the whole range less Briggs's entries, and prints the size of the gap.",
        instructions = """
            1. Save the example as vlacq_gap.py.
            2. Run it: python3 vlacq_gap.py
        """,
        example = """
            WHOLE_RANGE = (1, 100000)
            BRIGGS_RANGES = [(1, 20000), (90000, 100000)]
            briggs = sum(hi - lo + 1 for lo, hi in BRIGGS_RANGES)
            whole = WHOLE_RANGE[1] - WHOLE_RANGE[0] + 1
            gap = whole - briggs
            print("numbers Briggs left out:", gap)
            print("check, gap from 20001 to 89999:", gap == 89999 - 20001 + 1)
        """,
    ),

)
