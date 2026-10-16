package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 1480 to 1490: the remainder of three years, the orders of four quires and the powers of Chuquet's notation. */
val db56Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 56,
        kind = Kind.FLEX,
        section = "Arithmetic",
        title = "The remainder of three years on division by seven",
        originalTitle = "Remainder",
        author = "Arithmetic teachers, anonymous",
        year = 1488,
        summary = "A remainder is what is left over after dividing by seven, and a short program prints the remainders of three years of the decade.",
        details = "The years 1480, 1488 and 1490 leave remainders of three, four and six when divided by seven, and the example prints each one. The year chosen for the record is the one in which the Soncino Bible was printed, and the exercise is a plain check of division.",
        instructions = """
            1. Save the example as remainders.py.
            2. Run it: python3 remainders.py
        """,
        example = """
            for year in (1480, 1488, 1490):
                print(f"{year} mod 7 = {year % 7}")
        """,
    ),

    entry(
        databaseId = 56,
        kind = Kind.FLEX,
        section = "Binding",
        title = "The orders of four bound quires",
        originalTitle = "Permutations",
        author = "Binders of the 1480s",
        year = 1480,
        summary = "Four quires can be bound in twenty-four different orders, and a short program counts the orders and prints the first three.",
        details = "A binder who gathers four quires has four choices for the first, three for the second, two for the third and one for the last, which gives twenty-four orders in all. The example lists the first three of them in the order a program generates them.",
        instructions = """
            1. Save the example as quire_orders.py.
            2. Run it: python3 quire_orders.py
        """,
        example = """
            from itertools import permutations

            orders = list(permutations("ABCD"))
            print("orders of four quires:", len(orders))
            for order in orders[:3]:
                print("".join(order))
        """,
    ),

    entry(
        databaseId = 56,
        kind = Kind.FLEX,
        section = "Algebra",
        title = "Chuquet's powers multiplied by adding exponents",
        originalTitle = "Le Triparty en la science des nombres",
        author = "Nicolas Chuquet",
        year = 1484,
        summary = "When two powers of one base are multiplied, their exponents add, and a short program checks the rule for twelve squared times twelve cubed.",
        details = "Chuquet's Triparty of 1484 handled powers of this kind, and the example writes them in the modern caret notation rather than in his own. The program prints the product as a power and as a whole number.",
        instructions = """
            1. Save the example as exponent_rule.py.
            2. Run it: python3 exponent_rule.py
        """,
        example = """
            base = 12
            a, b = 2, 3
            product_exponent = a + b
            print(f"{base}^{a} x {base}^{b} = {base}^{product_exponent}")
            print(f"value: {base ** product_exponent}")
        """,
    ),

)
