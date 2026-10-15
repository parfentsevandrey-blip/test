package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 1740 to 1750: a least-action path, a binomial series, a whist deal and the cycle of the lunar node. */
val db30Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 30,
        kind = Kind.PROJECT,
        section = "Least action",
        title = "Maupertuis's least action at a boundary",
        originalTitle = "Accord de differentes lois de la nature qui avoient jusqu'ici paru incompatibles",
        author = "Pierre Louis Moreau de Maupertuis",
        year = 1744,
        summary = "A moving body crossing a boundary takes the path that makes its action, speed times distance, least, and the program finds that crossing on a grid.",
        details = "Maupertuis presented his principle of least action in 1744 and used it to derive the law of refraction, in which the speed on each side enters the rule. The example takes speeds 3 and 4 on the two sides of a boundary, with the two points 3 and 4 units from it and 7 units apart, and finds the crossing at 4, where the action is 35.",
        instructions = """
            1. Save the example as maupertuis.py.
            2. Run it: python3 maupertuis.py
        """,
        example = """
            from fractions import Fraction as F
            from math import sqrt

            V1, V2 = 3.0, 4.0          # speeds above and below the boundary, in the example's units
            A, B, D = 3.0, 4.0, 7.0    # height above, depth below, and horizontal distance between the two points

            def action(x):
                # Speed times path length on each side of the boundary, added together.
                return V1 * sqrt(x * x + A * A) + V2 * sqrt((D - x) * (D - x) + B * B)

            grid = [k / 2 for k in range(0, 15)]   # crossing points 0, 1/2, 1, ..., 7
            best = min(grid, key=action)
            print("best crossing on the half-unit grid:", best)
            print("least action:", action(best))
            print("action is smallest at the best crossing:", all(action(best) < action(x) for x in grid if x != best))
            print("speed times sine agrees on both sides:", F(3) * F(4, 5) == F(4) * F(3, 5))
        """,
    ),

    entry(
        databaseId = 30,
        kind = Kind.PROJECT,
        section = "Series",
        title = "Euler's binomial series for a square root",
        originalTitle = "Introductio in analysin infinitorum",
        author = "Leonhard Euler",
        year = 1748,
        summary = "A power of one plus a small number is written as a series whose coefficients come from the binomial rule with a fractional exponent, and the program adds its first terms.",
        details = "Euler's Introductio of 1748 expands powers of sums by the binomial rule for any exponent, including one half. The example takes x equal to 1/4, adds the first one to four terms of the series for the square root of one plus x, and compares the four-term sum with the root, which is about 1.118034.",
        instructions = """
            1. Save the example as euler_binomial.py.
            2. Run it: python3 euler_binomial.py
        """,
        example = """
            from fractions import Fraction as F

            def sqrt_series(x, terms):
                # Binomial series for (1 + x) to the power 1/2: the coefficient of x^k is C(1/2, k).
                total, coef = F(0), F(1)
                for k in range(terms):
                    total += coef * x ** k
                    coef = coef * (F(1, 2) - k) / (k + 1)
                return total

            x = F(1, 4)   # approximates sqrt(1 + 1/4) = sqrt(5) / 2
            for terms in (1, 2, 3, 4):
                print(f"partial sum of {terms}: {sqrt_series(x, terms)}")
            print("decimal of the 4-term sum:", f"{float(sqrt_series(x, 4)):.6f}")
            print("true value of sqrt(5)/2 to six places:", f"{5 ** 0.5 / 2:.6f}")
        """,
    ),

    entry(
        databaseId = 30,
        kind = Kind.PROJECT,
        section = "Card games",
        title = "Hoyle's whist trick resolver",
        originalTitle = "A Short Treatise on the Game of Whist",
        author = "Edmond Hoyle",
        year = 1742,
        summary = "A trick goes to the highest trump, or otherwise to the highest card of the suit led, and the program resolves four tricks of a fixed deal with spades as trump.",
        details = "Hoyle's treatise of 1742 set out the rules of whist as the game was then played, including the trump suit and the duty to follow suit. The example resolves four tricks of a fixed deal, finds the winner of each, and counts the tricks won by each side.",
        instructions = """
            1. Save the example as whist.py.
            2. Run it: python3 whist.py
        """,
        example = """
            RANKS = {"2": 2, "3": 3, "4": 4, "5": 5, "6": 6, "7": 7, "8": 8, "9": 9, "10": 10,
                     "J": 11, "Q": 12, "K": 13, "A": 14}

            def winner(trick, trump):
                # Each play is (player, card), where a card is a rank followed by a suit letter, such as KH.
                led_suit = trick[0][1][-1]

                def strength(card):
                    rank, suit = RANKS[card[:-1]], card[-1]
                    if suit == trump:
                        return 100 + rank       # a trump beats any card of the suit led
                    if suit == led_suit:
                        return rank             # otherwise the highest card of the suit led wins
                    return 0                    # a discard can never win

                return max(trick, key=lambda play: strength(play[1]))[0]

            tricks = [
                [("N", "KH"), ("E", "2H"), ("S", "7H"), ("W", "AH")],
                [("W", "9S"), ("N", "2S"), ("E", "QS"), ("S", "3D")],
                [("E", "5C"), ("S", "10C"), ("W", "3C"), ("N", "JD")],
                [("S", "4H"), ("W", "QH"), ("N", "8H"), ("E", "10H")],
            ]

            winners = [winner(trick, "S") for trick in tricks]
            print("winners:", winners)
            ew = sum(1 for w in winners if w in ("E", "W"))
            print(f"East-West tricks: {ew}, North-South tricks: {len(winners) - ew}")
            print("trumps used:", sum(1 for trick in tricks for _, card in trick if card[-1] == "S"))
        """,
    ),

    entry(
        databaseId = 30,
        kind = Kind.PROJECT,
        section = "Astronomy",
        title = "Bradley's cycle of the lunar node",
        originalTitle = "Letter on the nutation of the earth's axis",
        author = "James Bradley",
        year = 1748,
        summary = "The Moon's node returns to the same place in about 6798 days, a cycle of roughly eighteen and a half years, and the program counts the cycles in longer spans.",
        details = "Bradley published his account of the nutation of the Earth's axis in 1748, and the nodal cycle of about 18.6 years lies behind it. The example divides about 6798 days by the Julian year of 365.25 days and counts the cycles in 186 years and in a century.",
        instructions = """
            1. Save the example as node_cycle.py.
            2. Run it: python3 node_cycle.py
        """,
        example = """
            DAYS_PER_YEAR = 365.25     # the Julian year, in days
            NODE_CYCLE_DAYS = 6798     # about the period of the Moon's node, in whole days

            def years(days):
                return days / DAYS_PER_YEAR

            print(f"years per cycle: {years(NODE_CYCLE_DAYS):.2f}")
            print(f"cycles in 186 years: {186 * DAYS_PER_YEAR / NODE_CYCLE_DAYS:.2f}")
            print(f"cycles in a century: {100 * DAYS_PER_YEAR / NODE_CYCLE_DAYS:.2f}")
        """,
    ),
)
