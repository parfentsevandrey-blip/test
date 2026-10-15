package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 1680 to 1690: Wallis's imaginary product, Cassini's moons of Saturn and pence in a pound. */
val db36Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 36,
        kind = Kind.FLEX,
        section = "Algebra",
        title = "Wallis's imaginary numbers multiplied in one line",
        originalTitle = "A Treatise of Algebra, both Historical and Practical",
        author = "John Wallis",
        year = 1685,
        summary = "Multiplying the imaginary pair 1 + 2i and 1 - 2i gives the real number 5, which the one-line program prints.",
        details = "Wallis's treatise of 1685 let imaginary roots enter algebra, and a conjugate pair of roots multiplies to a real number. The program is one line of Python, and its output shows the imaginary part as zero.",
        instructions = """
            1. Save the example as wallis_imaginary.py.
            2. Run it: python3 wallis_imaginary.py
        """,
        example = """
            print("(1 + 2i) times (1 - 2i):", (1 + 2j) * (1 - 2j))
        """,
    ),

    entry(
        databaseId = 36,
        kind = Kind.FLEX,
        section = "Astronomy",
        title = "Saturn's moons known in 1686, counted in one line",
        originalTitle = "Saturn's moons known in 1686",
        author = "Giovanni Domenico Cassini",
        year = 1684,
        summary = "By 1686 five moons were known to circle Saturn, and the one-line program counts them from a list of their names.",
        details = "Huygens found Titan in 1655, and Cassini found Iapetus in 1671 and Rhea in 1672, then Tethys and Dione in 1684. The one line counts the five names, so the program prints how many moons were known by 1686.",
        instructions = """
            1. Save the example as saturn_moons.py.
            2. Run it: python3 saturn_moons.py
        """,
        example = """
            print("moons of Saturn known in 1686:", len(["Titan", "Iapetus", "Rhea", "Tethys", "Dione"]))
        """,
    ),

    entry(
        databaseId = 36,
        kind = Kind.FLEX,
        section = "Money",
        title = "Pence in a pound of old English money in one line",
        originalTitle = "Pounds, shillings and pence",
        author = "English money of account",
        year = 1680,
        summary = "Old English money counted twelve pence to the shilling and twenty shillings to the pound, so the one-line program prints 240 pence in a pound.",
        details = "Old English money counted twelve pence to the shilling and twenty shillings to the pound, which is the arithmetic behind the account books of the 1680s. The program multiplies twenty shillings by twelve pence, and the answer is 240.",
        instructions = """
            1. Save the example as pence_pound.py.
            2. Run it: python3 pence_pound.py
        """,
        example = """
            print("pence in a pound:", 20 * 12)
        """,
    ),

)
