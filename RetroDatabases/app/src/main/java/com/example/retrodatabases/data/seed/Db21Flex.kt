package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 1830 to 1840: the EMF of a coil, cells joined in series and the patterns of a six-indicator telegraph. */
val db21Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 21,
        kind = Kind.FLEX,
        section = "Induction",
        title = "Faraday's law of induction for a coil of turns",
        originalTitle = "Experimental Researches in Electricity, first series (1831)",
        author = "Michael Faraday",
        year = 1831,
        summary = "The EMF of a coil is the number of turns times the rate at which the flux through each turn changes, so a fall in flux gives an EMF whose size follows from those two numbers.",
        details = "Faraday's induction experiments of 1831 led to this relation, which later became the law named after him. The example takes a 100-turn coil and a flux change chosen for the calculation, and it prints the size of the EMF in volts. The record uses the 1831 date of the experiments.",
        instructions = """
            1. Save the example as faraday_coil.py.
            2. Run it: python3 faraday_coil.py
        """,
        example = """
            N = 100              # turns in the coil
            flux_before = 2e-3   # webers through one turn at the start
            flux_after = 0.0     # webers through one turn after the change
            dt = 0.5             # seconds taken by the change
            emf = N * (flux_after - flux_before) / dt
            print(f"coil of {N} turns: EMF magnitude {abs(emf):.2f} V")
        """,
    ),

    entry(
        databaseId = 21,
        kind = Kind.FLEX,
        section = "Batteries",
        title = "Daniell cells in series for a telegraph circuit",
        originalTitle = "On a constant voltaic battery (1836)",
        author = "John Frederic Daniell",
        year = 1836,
        summary = "Cells in series add their voltages and their internal resistances, so the current through a fixed line is set by the number of cells and the total resistance.",
        details = "Daniell's constant cell of 1836 was used in telegraph batteries, and cells were joined in series to raise the voltage on long lines. The example takes one voltage and one internal resistance per cell, chosen for the example, and prints the current for one, six and ten cells. The record uses the 1836 date of the cell.",
        instructions = """
            1. Save the example as daniell_series.py.
            2. Run it: python3 daniell_series.py
        """,
        example = """
            E = 1.0     # volts per cell, a rounded value chosen for this example
            r = 2.0     # ohms inside each cell, chosen for this example
            R = 100.0   # ohms in the line and the instruments
            for n in (1, 6, 10):
                current = n * E / (n * r + R)
                print(f"{n} cell circuit: current {current * 1000:.2f} mA")
        """,
    ),

    entry(
        databaseId = 21,
        kind = Kind.FLEX,
        section = "Needle telegraph",
        title = "Schilling's six indicators and 64 symbols",
        originalTitle = "Schilling's telegraph at St Petersburg (1832)",
        author = "Pavel Schilling",
        year = 1832,
        summary = "Six indicators that each show one of two states give 64 patterns, enough for letters, numerals and signs in a needle telegraph.",
        details = "Schilling's telegraph of 1832 used six needle indicators, and each could take one of two positions, so the set gave 64 patterns. The example prints the count and four patterns in six-bit form. The record uses the 1832 date of the demonstration.",
        instructions = """
            1. Save the example as schilling_bits.py.
            2. Run it: python3 schilling_bits.py
        """,
        example = """
            symbols = 64
            bits = 6
            print(f"{bits} indicators give {2 ** bits} patterns, enough for {symbols} symbols")
            for n in (0, 1, 37, 63):
                pattern = format(n, "06b")
                print(f"symbol {n} is pattern {pattern}")
        """,
    ),
)
