package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 1780 to 1790: short programs for eclipses, dark stars, friction, cycles of votes, horsepower and impacts. */
val db26Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 26,
        kind = Kind.EXAMPLE,
        section = "Variable stars",
        title = "Predicting the eclipses of Algol from its period",
        originalTitle = "A Series of Observations on, and a Discovery of, the Period of the Variation of the Light of the Bright Star in the Head of Medusa",
        author = "John Goodricke",
        year = 1783,
        summary = "Algol dims at a regular interval, so a period in whole minutes is enough to predict its later minima, which the program prints as days and clock times.",
        details = "Goodricke announced in 1783 that the light of Algol falls and recovers at regular intervals, with a period of about two days and twenty hours. The example adds a period of 2 days 20 hours 49 minutes to an epoch in whole minutes and prints the next four predicted minima. That period is a rounded modern value, so the times are for the example only.",
        instructions = """
            1. Save the example as algol.py.
            2. Run it: python3 algol.py
        """,
        example = """
            PERIOD = 2 * 24 * 60 + 20 * 60 + 49   # 2 days 20 hours 49 minutes, in minutes (a rounded modern value)

            def clock(minutes):
                # Days, then hours and minutes, written as d hh:mm.
                d, rest = divmod(minutes, 24 * 60)
                h, m = divmod(rest, 60)
                return f"{d} d {h:02d}:{m:02d}"

            for k in range(1, 5):
                print(f"minimum {k}: {clock(k * PERIOD)}")
            print("period in minutes:", PERIOD)
        """,
    ),

    entry(
        databaseId = 26,
        kind = Kind.EXAMPLE,
        section = "Astronomy",
        title = "Michell's dark star: escape speed at a fixed density",
        originalTitle = "On the Means of Discovering the Distance, Magnitude, &c. of the Fixed Stars",
        author = "John Michell",
        year = 1784,
        summary = "A star of the Sun's density but 500 times its radius would have an escape speed 500 times the Sun's, which the program compares with the speed of light.",
        details = "Michell's paper, read in 1783 and printed in 1784, used Newtonian gravity and the speed of light to argue that some stars might be too massive for their light to escape. The example uses the Sun's escape speed of 617.5 km/s and the speed of light, 299792 km/s, to find the radius ratio at which a star of solar density would keep its light, which is about 485. Michell's example used a star 500 times the Sun's diameter, which lies above that ratio.",
        instructions = """
            1. Save the example as michell.py.
            2. Run it: python3 michell.py
        """,
        example = """
            from fractions import Fraction as F

            SUN_ESCAPE = F(6175, 10)   # 617.5 km/s, the Sun's escape speed, rounded to one decimal
            C = 299792                 # speed of light in km/s

            def escape_speed(ratio):
                # Escape speed squared is 2GM/R; at the same density M grows as R cubed, so the speed grows as R.
                return SUN_ESCAPE * ratio

            v500 = escape_speed(500)
            print(f"escape speed at 500 solar radii: {v500} km/s")
            print("above the speed of light:", v500 > C)
            threshold = C / SUN_ESCAPE
            print(f"radius ratio where escape speed reaches c: {float(threshold):.2f}")
            print("500 is above the threshold:", 500 > threshold)
        """,
    ),

    entry(
        databaseId = 26,
        kind = Kind.EXAMPLE,
        section = "Friction",
        title = "Coulomb's friction on an inclined plane",
        originalTitle = "Theorie des machines simples, en ayant egard au frottement de leurs parties, et a la roideur des cordages",
        author = "Charles-Augustin de Coulomb",
        year = 1781,
        summary = "A block on a plane slides once the slope, the tangent of the angle, is greater than the friction coefficient, and the program compares slopes in exact fractions.",
        details = "Coulomb's memoir on simple machines won the Academy's prize of 1781 and set out the friction law, under which sliding friction is a fixed fraction of the normal load; it was printed in volume ten of the Academy's Memoires in 1785, and accounts differ on the original deadline. The example takes a friction coefficient of 3/5 and compares the slopes 1/2, 3/5 and 2/3 in exact fractions, so the block stays on the first two and slides on the third.",
        instructions = """
            1. Save the example as coulomb.py.
            2. Run it: python3 coulomb.py
        """,
        example = """
            from fractions import Fraction as F

            MU = F(3, 5)   # coefficient of friction

            def status(slope):
                # On a plane whose slope is tan(theta), friction holds the block while the slope is at most mu.
                if slope < MU:
                    return "stays"
                if slope == MU:
                    return "stays, at the limit"
                return "slides"

            slopes = [F(1, 2), F(3, 5), F(2, 3)]
            for slope in slopes:
                print(f"slope {slope}: {status(slope)}")
            print("steepest slope that holds:", max(s for s in slopes if status(s) != "slides"))
        """,
    ),

    entry(
        databaseId = 26,
        kind = Kind.EXAMPLE,
        section = "Voting",
        title = "Condorcet's cycle of majorities among three voters",
        originalTitle = "Essai sur l'application de l'analyse a la probabilite des decisions rendues a la pluralite des voix",
        author = "Marie Jean Antoine Nicolas de Caritat, marquis de Condorcet",
        year = 1785,
        summary = "Three voters with ranked preferences can produce a majority cycle in which A beats B, B beats C and C beats A, which the program checks pair by pair.",
        details = "Condorcet's essay of 1785 also showed that majority votes can be cyclic. The example uses three voters who each rank three candidates, tallies each pair, and finds that each candidate beats one rival by two votes to one, so the majorities form a loop. The same loop is often called the Condorcet paradox.",
        instructions = """
            1. Save the example as condorcet_cycle.py.
            2. Run it: python3 condorcet_cycle.py
        """,
        example = """
            from itertools import combinations

            ballots = [
                ["A", "B", "C"],   # voter 1 ranks A first, then B, then C
                ["B", "C", "A"],   # voter 2
                ["C", "A", "B"],   # voter 3
            ]

            def prefers(ballot, x, y):
                return ballot.index(x) < ballot.index(y)

            def contest(x, y):
                # Majority between x and y: returns (winner, winner's votes, loser's votes).
                x_votes = sum(prefers(b, x, y) for b in ballots)
                y_votes = len(ballots) - x_votes
                if x_votes > y_votes:
                    return x, x_votes, y_votes
                return y, y_votes, x_votes

            beats = {}
            for x, y in combinations("ABC", 2):
                winner, w, l = contest(x, y)
                loser = y if winner == x else x
                beats[winner] = loser
                print(f"{winner} beats {loser}: {w} to {l}")

            path = ["A"]
            for _ in range(3):
                path.append(beats[path[-1]])
            print("cycle: " + " > ".join(path))
            print("a full loop of three:", path[0] == path[3] and len(set(path[:3])) == 3)
        """,
    ),

    entry(
        databaseId = 26,
        kind = Kind.EXAMPLE,
        section = "Engines",
        title = "Watt's horsepower: a unit of work for engines",
        originalTitle = "Horse-power",
        author = "James Watt",
        year = 1782,
        summary = "Watt measured the work of one horse as 33,000 foot-pounds a minute, so an engine's power could be compared with a team of horses.",
        details = "Watt's figure of 33,000 foot-pounds a minute for one horse is dated here to 1782; accounts differ on the year and on how he measured a horse. The example converts a lift of 1000 pounds at 2 feet a second into horsepower, which comes to 40/11, and prints its decimal value.",
        instructions = """
            1. Save the example as horsepower.py.
            2. Run it: python3 horsepower.py
        """,
        example = """
            from fractions import Fraction as F

            FOOT_POUNDS_PER_MINUTE = 33000   # Watt's figure for one horse, in foot-pounds of work a minute
            FOOT_POUNDS_PER_SECOND = F(FOOT_POUNDS_PER_MINUTE, 60)

            def horsepower(pounds, feet_per_second):
                # Work per second is force times speed, in foot-pounds a second; divide by the rate of one horse.
                return F(pounds) * F(feet_per_second) / FOOT_POUNDS_PER_SECOND

            print("one horsepower in foot-pounds per second:", FOOT_POUNDS_PER_SECOND)
            hp = horsepower(1000, 2)
            print("lifting 1000 pounds at 2 feet a second:", hp, "horsepower")
            print("as a decimal:", f"{float(hp):.4f}")
        """,
    ),

    entry(
        databaseId = 26,
        kind = Kind.EXAMPLE,
        section = "Impact",
        title = "Carnot's energy lost in an inelastic impact",
        originalTitle = "Essai sur les machines en general",
        author = "Lazare Carnot",
        year = 1783,
        summary = "When two bodies stick together after an impact, momentum fixes their common speed, and the program checks the kinetic energy lost against Carnot's formula.",
        details = "Carnot's essay of 1783 on machines in general treated impacts and gave the energy lost when two bodies stick together, which is one half of the reduced mass times the square of the relative speed. The example collides a mass of 3 moving at 4 with a mass of 1 at rest, finds the common speed 3 from momentum, and checks that 6 units of energy are lost.",
        rare = true,
        instructions = """
            1. Save the example as carnot.py.
            2. Run it: python3 carnot.py
        """,
        example = """
            from fractions import Fraction as F

            def collide(m1, u1, m2, u2):
                # Perfectly inelastic impact: the bodies stick together, and momentum fixes their common velocity.
                v = F(m1 * u1 + m2 * u2, m1 + m2)
                before = F(1, 2) * m1 * u1**2 + F(1, 2) * m2 * u2**2
                after = F(1, 2) * (m1 + m2) * v**2
                return v, before, after

            v, before, after = collide(3, 4, 1, 0)
            print("common velocity:", v)
            print("kinetic energy before:", before, "after:", after)
            print("energy lost:", before - after)
            # Carnot's loss: one half of the reduced mass times the square of the relative velocity.
            reduced = F(3 * 1, 3 + 1)
            print("formula agrees:", before - after == F(1, 2) * reduced * (4 - 0) ** 2)
        """,
    ),
)
