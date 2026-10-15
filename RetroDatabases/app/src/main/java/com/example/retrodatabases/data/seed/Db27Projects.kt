package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 1770 to 1780: wires for letters, branching figures, a school day in lesson blocks and the present value of yearly payments. */
val db27Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 27,
        kind = Kind.PROJECT,
        section = "Schools",
        title = "Basedow's school day in lesson blocks",
        originalTitle = "Philanthropinum",
        author = "Johann Bernhard Basedow",
        year = 1774,
        summary = "Basedow opened his Philanthropinum at Dessau in 1774, and the program divides an illustrative school day into lesson blocks of equal length.",
        details = "Basedow founded the Philanthropinum at Dessau in 1774 to teach modern subjects alongside the classics. The example uses an illustrative day from eight o'clock to one o'clock and lessons of 45 minutes, not a recorded timetable. It prints the day's length, the full lessons, the minutes left over and the share of the day spent in lessons.",
        instructions = """
            1. Save the example as school_day.py.
            2. Run it: python3 school_day.py
        """,
        example = """
            from fractions import Fraction as F

            start, end = 8 * 60, 13 * 60     # illustrative school day, in minutes after midnight, from 8:00 to 13:00
            lesson = 45                      # illustrative lesson length in minutes
            day = end - start
            print(f"day length: {day} minutes")
            print(f"full lessons: {day // lesson}")
            print(f"minutes left over: {day % lesson}")
            print(f"fraction of the day in lessons: {F((day // lesson) * lesson, day)}")
        """,
    ),

    entry(
        databaseId = 27,
        kind = Kind.PROJECT,
        section = "Telegraphs",
        title = "Letters sent wire by wire on Le Sage's telegraph",
        originalTitle = "Electrostatic telegraph",
        author = "Georges-Louis Le Sage",
        year = 1774,
        summary = "Le Sage's electrostatic telegraph gave each letter a wire of its own, and the program finds the wires that send a short word.",
        details = "Le Sage built his telegraph in Geneva in 1774, with twenty-four wires, each charged to signal one letter at the receiving end. The example uses an illustrative numbering of the letters A to X as wires 1 to 24, which is not Le Sage's own table, and sends the word TELL. It prints the wires used, the number of distinct wires and the number of charges sent.",
        rare = true,
        instructions = """
            1. Save the example as wires.py.
            2. Run it: python3 wires.py
        """,
        example = """
            from string import ascii_uppercase

            WIRES = ascii_uppercase[:24]     # 24 letters, A to X, one wire each (an illustrative numbering)

            def wires_for(word):
                # Each letter is sent by charging the wire that belongs to it.
                return [WIRES.index(ch) + 1 for ch in word]

            codes = wires_for("TELL")
            print("wires for TELL:", codes)
            print("distinct wires used:", len(set(codes)))
            print("charges sent:", len(codes))
        """,
    ),

    entry(
        databaseId = 27,
        kind = Kind.PROJECT,
        section = "Annuities",
        title = "Present value of three yearly payments at one tenth",
        originalTitle = "Observations on Reversionary Payments",
        author = "Richard Price",
        year = 1771,
        summary = "The present value of a fixed number of yearly payments is found by discounting each one, and the program computes it in exact fractions for three payments at a rate of one tenth.",
        details = "Richard Price's book of 1771 on reversionary payments set out how to value sums that fall due in the future, by discounting each payment back to the present. The example uses the simpler case of a fixed number of yearly payments, which the same method values. It discounts three payments of 1 at the end of each year at a rate of one tenth, and prints the value as an exact fraction and to four decimal places.",
        instructions = """
            1. Save the example as annuity.py.
            2. Run it: python3 annuity.py
        """,
        example = """
            from fractions import Fraction as F

            rate = F(1, 10)                      # interest at one tenth a year
            v = 1 / (1 + rate)                   # discount factor for one year, here 10/11

            def present_value(payments, v):
                # Each payment of 1 at the end of year t is worth v to the power t today; the values are added.
                return sum(v ** t for t in range(1, payments + 1))

            pv = present_value(3, v)
            print(f"present value of 3 payments of 1: {pv}")
            print(f"as a decimal: {float(pv):.4f}")
        """,
    ),

    entry(
        databaseId = 27,
        kind = Kind.PROJECT,
        section = "Electrical figures",
        title = "Lichtenberg's figures grown by splitting branches",
        originalTitle = "Lichtenberg figures",
        author = "Georg Christoph Lichtenberg",
        year = 1777,
        summary = "Lichtenberg studied branching figures that electrified dust made on resin, and the program grows a branching pattern from a simple splitting rule.",
        details = "Lichtenberg began to study the branching figures in 1777, and they are named after him. The splitting rule used here is a model, not a measurement of his figures, and each tip splits into two at every generation. The example prints the branches and tips for four generations, which come to 30 branches in all.",
        instructions = """
            1. Save the example as lichtenberg.py.
            2. Run it: python3 lichtenberg.py
        """,
        example = """
            def grow(generations):
                # Each tip sends out two branches, and each branch ends in a new tip.
                tips = 1
                total_branches = 0
                for g in range(1, generations + 1):
                    new_branches = 2 * tips
                    total_branches += new_branches
                    tips = 2 * tips
                    print(f"generation {g}: {new_branches} new branches, {tips} tips")
                print(f"total branches after {generations} generations: {total_branches}")

            grow(4)
        """,
    ),

)
