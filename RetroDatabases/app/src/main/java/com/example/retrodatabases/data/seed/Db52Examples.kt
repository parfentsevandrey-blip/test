package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 1520 to 1530: money changed at a rate, Duerer's eight heads, Hebrew letter numerals, doubling at simple interest, a wall's courses and grain measured in bushels. */
val db52Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 52,
        kind = Kind.EXAMPLE,
        section = "Money",
        title = "Exchanging gold and silver pieces at a set rate",
        originalTitle = "Ein newe und wolgegruendte underweisung aller Kauffmans Rechnung",
        author = "Peter Apian",
        year = 1527,
        summary = "Merchant arithmetic of the 1520s changed money at a fixed rate, and a short program turns gold pieces into silver ones and back, with the change left over.",
        details = "Apian's merchant reckoning of 1527 is one book of that kind, and such books set exchange problems at a rate fixed for the day. The example uses an illustrative rate of 24 silver pieces to one gold piece, which is not a historical rate, and it keeps the change in whole pieces.",
        instructions = """
            1. Save the example as coin_exchange.py.
            2. Run it: python3 coin_exchange.py
        """,
        example = """
            RATE = 24   # illustrative only: one gold piece is taken as 24 silver pieces

            def gold_to_silver(gold):
                return gold * RATE

            def silver_to_gold(silver):
                gold, change = divmod(silver, RATE)   # whole gold pieces, and the silver left over
                return gold, change

            print(f"7 gold pieces give {gold_to_silver(7)} silver pieces")
            gold, change = silver_to_gold(1000)
            print(f"1000 silver pieces give {gold} gold pieces and {change} silver pieces change")
        """,
    ),

    entry(
        databaseId = 52,
        kind = Kind.EXAMPLE,
        section = "Proportion",
        title = "Duerer's eight heads for a standing figure",
        originalTitle = "Vier Buecher von menschlicher Proportion",
        author = "Albrecht Duerer",
        year = 1528,
        summary = "Duerer's canon of eight heads makes a standing figure eight head-lengths tall, with the navel at half that height, and a short program checks three head sizes.",
        details = "The books of human proportion were printed at Nuremberg in 1528, after Duerer's death in April of that year, and they give the canon of eight heads for a man. The example uses three head sizes and prints the height and the place of the navel, which come out as whole numbers in the canon.",
        instructions = """
            1. Save the example as duerer_heads.py.
            2. Run it: python3 duerer_heads.py
        """,
        example = """
            HEADS = 8   # the canon: a standing man is eight heads tall

            for head in (20, 22, 24):
                height = HEADS * head
                navel = height // 2     # the navel falls at the middle of the figure
                print(f"head {head} units: height {height} units, navel at {navel} units")
        """,
    ),

    entry(
        databaseId = 52,
        kind = Kind.EXAMPLE,
        section = "Numerals",
        title = "Hebrew letters as numbers, with 15 written tet vav",
        originalTitle = "Hebrew letter numerals",
        author = "Hebrew scribal practice",
        year = 1524,
        summary = "Hebrew letters carry numbers, so that 15 is written tet vav rather than yod he, and a short program spells five numbers in those letters.",
        details = "Hebrew books of the 1520s used the letters of the alphabet for numbers, and 15 is written tet vav, not yod he, to avoid writing the divine name. The example subtracts the largest letter value that fits, again and again, so each number has one spelling.",
        instructions = """
            1. Save the example as hebrew_numerals.py.
            2. Run it: python3 hebrew_numerals.py
        """,
        example = """
            LETTERS = [(400, "tav"), (300, "shin"), (200, "resh"), (100, "qof"), (90, "tsade"), (80, "pe"),
                       (70, "ayin"), (60, "samekh"), (50, "nun"), (40, "mem"), (30, "lamed"), (20, "kaf"),
                       (10, "yod"), (9, "tet"), (8, "het"), (7, "zayin"), (6, "vav"), (5, "he"), (4, "dalet"),
                       (3, "gimel"), (2, "bet"), (1, "alef")]

            def spell(n):
                # subtract the largest letter value that fits, again and again
                names = []
                for value, name in LETTERS:
                    while n >= value:
                        names.append(name)
                        n -= value
                return names

            def hebrew_numeral(n):
                if n % 100 in (15, 16):
                    # 15 and 16 are written tet vav and tet zayin, not yod he and yod vav, to avoid the divine name
                    return " ".join(spell(n - n % 100) + ["tet"] + spell(n % 100 - 9))
                return " ".join(spell(n))

            for n in (15, 16, 26, 300, 412):
                print(n, hebrew_numeral(n))
        """,
    ),

    entry(
        databaseId = 52,
        kind = Kind.EXAMPLE,
        section = "Interest",
        title = "Years for a sum to double at simple interest",
        originalTitle = "Years for a sum to double at simple interest",
        author = "Merchant arithmetic of the 1520s",
        year = 1527,
        summary = "At simple interest a sum doubles after a hundred divided by the rate in years, and a short program gives the years at three rates.",
        details = "Interest problems in merchant arithmetic asked how long a sum took to double, and under simple interest the answer is a hundred divided by the rate. The example uses rates of five, four and eight in the hundred and keeps the answers as exact fractions, so 25/2 years is not rounded.",
        instructions = """
            1. Save the example as doubling_years.py.
            2. Run it: python3 doubling_years.py
        """,
        example = """
            from fractions import Fraction as F

            for percent in (5, 4, 8):
                years = F(100, percent)      # at simple interest a sum doubles after 100 divided by the rate, in years
                print(f"at {percent} in the hundred, a sum doubles in {years} years")
        """,
    ),

    entry(
        databaseId = 52,
        kind = Kind.EXAMPLE,
        section = "Building",
        title = "Stones in a wall, course by course",
        originalTitle = "Stones in a wall, course by course",
        author = "German counting books of the 1520s",
        year = 1522,
        summary = "A wall of twelve courses, with 40 stones in the bottom course and two fewer in each course above, holds 348 stones, and a short program adds them up.",
        details = "Problems of this kind, a sum of numbers that fall by equal steps, were set in the counting books of the 1520s for builders and merchants. The example prints the number of courses, the top course and the total number of stones.",
        instructions = """
            1. Save the example as wall_courses.py.
            2. Run it: python3 wall_courses.py
        """,
        example = """
            bottom = 40
            courses = 12
            stones = [bottom - 2 * i for i in range(courses)]   # each course is two stones fewer than the one below
            print("courses:", len(stones))
            print("top course:", stones[-1])
            print("stones in the wall:", sum(stones))
        """,
    ),

    entry(
        databaseId = 52,
        kind = Kind.EXAMPLE,
        section = "Grain",
        title = "Pecks and bushels of grain, with the remainder",
        originalTitle = "Pecks and bushels of grain, with the remainder",
        author = "Grain measures, illustrative",
        year = 1522,
        summary = "A quantity of grain in pecks is split into whole bushels and the pecks left over, and a short program converts three amounts with divmod.",
        details = "Grain was sold by measure in the markets of the 1520s, and a sack counted in pecks had to be changed into bushels for the account. The example takes four pecks to the bushel as an illustrative measure, not a local standard, and prints the whole bushels and the remainder for three amounts.",
        instructions = """
            1. Save the example as grain_measures.py.
            2. Run it: python3 grain_measures.py
        """,
        example = """
            PECKS_PER_BUSHEL = 4        # illustrative measure: four pecks to the bushel

            for pecks in (37, 52, 9):
                bushels, rest = divmod(pecks, PECKS_PER_BUSHEL)   # whole bushels, and the pecks left over
                print(f"{pecks} pecks = {bushels} bushels with {rest} left over")
        """,
    ),

)
