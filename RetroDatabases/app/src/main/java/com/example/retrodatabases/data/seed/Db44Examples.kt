package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 1600 to 1610: a falling body's odd steps, the fading of light, three ships' tonnage, the books of a Bible, a sign count and a square completed. */
val db44Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 44,
        kind = Kind.EXAMPLE,
        section = "Falling bodies",
        title = "Galileo's odd numbers for a falling body",
        originalTitle = "Lettera a Paolo Sarpi",
        author = "Galileo Galilei",
        year = 1604,
        summary = "Galileo's rule of 1604 says that a falling body covers 1, 3, 5 and 7 units in successive equal intervals, so its total distance grows as the square of the time.",
        details = "Galileo stated the odd-number rule in a letter to Paolo Sarpi of 1604. The example prints the steps and the totals for one to five intervals, and each total is the square of the number of intervals.",
        instructions = """
            1. Save the example as falling_odd.py.
            2. Run it: python3 falling_odd.py
        """,
        example = """
            def odd_steps(n):
                # In each equal interval the body covers 1, then 3, then 5 units, and so on.
                return [2 * k + 1 for k in range(n)]

            for n in (1, 2, 3, 4, 5):
                steps = odd_steps(n)
                print(f"after {n} intervals: steps {steps}, total {sum(steps)}")
        """,
    ),

    entry(
        databaseId = 44,
        kind = Kind.EXAMPLE,
        section = "Optics",
        title = "Kepler's fading light, one over the square",
        originalTitle = "Ad Vitellionem paralipomena",
        author = "Johannes Kepler",
        year = 1604,
        summary = "Kepler's optics of 1604 holds that the light from a point weakens as one over the square of the distance, and a short program prints the fractions for five distances.",
        details = "The inverse-square rule for light is stated in Kepler's book of 1604, and the example prints the share of light at distances one to five. The fractions are exact, so the square pattern can be read directly from the output.",
        instructions = """
            1. Save the example as kepler_light.py.
            2. Run it: python3 kepler_light.py
        """,
        example = """
            from fractions import Fraction as F

            def relative_light(distance):
                # The strength of light from a point falls as one over the square of the distance.
                return F(1, distance * distance)

            for d in (1, 2, 3, 4, 5):
                print(f"distance {d}: light {relative_light(d)}")
        """,
    ),

    entry(
        databaseId = 44,
        kind = Kind.EXAMPLE,
        section = "Voyages",
        title = "The tonnage of the three ships that sailed to Jamestown",
        originalTitle = "Susan Constant, Godspeed and Discovery",
        author = "The Virginia Company of London",
        year = 1607,
        summary = "The three ships that carried the first settlers to Jamestown in 1607 had tonnages that a short program adds and averages.",
        details = "The ships were the Susan Constant, the Godspeed and the Discovery. Accounts differ on their tonnages, so the example uses one commonly cited set, 120, 40 and 20 tons, and totals and averages those figures.",
        instructions = """
            1. Save the example as jamestown_ships.py.
            2. Run it: python3 jamestown_ships.py
        """,
        example = """
            SHIPS = [("Susan Constant", 120), ("Godspeed", 40), ("Discovery", 20)]   # tons, as commonly given

            total = sum(tons for name, tons in SHIPS)
            print("ships:", len(SHIPS))
            print("total tons:", total)
            print("average tons:", total / len(SHIPS))
        """,
    ),

    entry(
        databaseId = 44,
        kind = Kind.EXAMPLE,
        section = "Bibles",
        title = "Counting the books of the Douay Bible of 1609",
        originalTitle = "The Holy Bible, faithfully translated into English, out of the authentic Latin",
        author = "The English College at Douai",
        year = 1609,
        summary = "The Old Testament of the Douay Bible was printed at Douai in 1609 and 1610, and with the New Testament it makes 73 books in the Catholic canon.",
        details = "The New Testament had been printed at Rheims in 1582, so the whole Douay Bible was completed over nearly three decades. The example adds the book counts, 46 for the Old Testament and 27 for the New, which are the standard counts for the Catholic canon.",
        instructions = """
            1. Save the example as douay_books.py.
            2. Run it: python3 douay_books.py
        """,
        example = """
            BOOKS = {"Old Testament": 46, "New Testament": 27}   # book counts, as commonly given

            for part, count in BOOKS.items():
                print(f"{part}: {count} books")
            print("all books:", sum(BOOKS.values()))
        """,
    ),

    entry(
        databaseId = 44,
        kind = Kind.EXAMPLE,
        section = "Shorthand",
        title = "Counting the signs for a word in a sign alphabet",
        originalTitle = "The Art of Stenography",
        author = "John Willis",
        year = 1602,
        summary = "A sign alphabet needs one sign for each letter, so a word of n letters takes n signs, and a program writes three words as sign numbers.",
        details = "Willis's book of 1602 gave each letter of the alphabet its own sign. The example numbers the letters from one to twenty-six, which is a modern way to count the signs and not Willis's own notation.",
        instructions = """
            1. Save the example as sign_count.py.
            2. Run it: python3 sign_count.py
        """,
        example = """
            SIGNS = {letter: index for index, letter in enumerate("abcdefghijklmnopqrstuvwxyz", start=1)}

            def sign_numbers(word):
                # Each letter takes one sign, so a word needs as many signs as it has letters.
                return [SIGNS[letter] for letter in word]

            for word in ("bread", "shorthand", "pen"):
                codes = sign_numbers(word)
                print(word, codes, "signs:", len(codes))
        """,
    ),

    entry(
        databaseId = 44,
        kind = Kind.EXAMPLE,
        section = "Algebra",
        title = "Completing the square for equations with whole roots",
        originalTitle = "Algebra Christophori Clavii Bambergensis e Societate Iesu",
        author = "Christoph Clavius",
        year = 1608,
        summary = "Completing the square turns x squared plus b x equals c into a square equal to a whole number, and a program finds the two roots of three such equations.",
        details = "Clavius's Algebra of 1608 is a textbook of the rules for solving equations, and completing the square is one of the standard rules of that kind. The example handles equations whose square root comes out whole, and it prints both roots of each.",
        instructions = """
            1. Save the example as complete_square.py.
            2. Run it: python3 complete_square.py
        """,
        example = """
            from math import isqrt

            def solve(b, c):
                # Completing the square: (x + b/2) squared equals c plus (b/2) squared, so the roots are -b/2 plus or minus its root.
                half = b // 2
                root = isqrt(c + half * half)
                return (-half + root, -half - root)

            for b, c in ((6, 7), (4, 5), (-2, 3)):
                print(f"x^2 + {b}x = {c}: roots {solve(b, c)}")
        """,
    ),

)
