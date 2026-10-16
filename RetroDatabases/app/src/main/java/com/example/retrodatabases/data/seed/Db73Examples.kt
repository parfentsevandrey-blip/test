package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 1310 to 1316: committees of the Council of Ten, wooden type for one sentence, the bones of the limbs, a chain pump, a trip hammer and the multiples of three in the decade. */
val db73Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 73,
        kind = Kind.EXAMPLE,
        section = "Councils",
        title = "How many three-member committees a Council of Ten could form",
        originalTitle = "Combinations of three",
        author = "No single author",
        year = 1310,
        summary = "A council of ten members can form a set number of committees of three, and a short program counts the committees and the pairs of members.",
        details = "The count uses combinations, in which the order of the members does not matter, so the ten councillors give 120 committees of three and 45 pairs. The example prints both counts, which can be checked against the formula by hand.",
        instructions = """
            1. Save the example as committees.py.
            2. Run it: python3 committees.py
        """,
        example = """
            from itertools import combinations
            from math import comb

            councillors = list(range(1, 11))
            print("committees of three:", len(list(combinations(councillors, 3))))
            print("pairs of councillors:", comb(10, 2))
        """,
    ),

    entry(
        databaseId = 73,
        kind = Kind.EXAMPLE,
        section = "Printing",
        title = "Pieces of wooden type needed for one sentence",
        originalTitle = "Letter count",
        author = "No single author",
        year = 1313,
        summary = "Each letter of a sentence set in wooden type needs one piece, and a short program counts the pieces and the different letters in a fixed sentence.",
        details = "The sentence in the example has 28 letters and 12 different letters, so the case needs 12 different pieces and the sentence uses 28 pieces in all. The count uses the letters only, so spaces are skipped.",
        instructions = """
            1. Save the example as type_pieces.py.
            2. Run it: python3 type_pieces.py
        """,
        example = """
            TEXT = "wang zhen cut each character once"
            letters = [ch for ch in TEXT if ch.isalpha()]
            print("letters in the sentence:", len(letters))
            print("different letters:", len(set(letters)))
        """,
    ),

    entry(
        databaseId = 73,
        kind = Kind.EXAMPLE,
        section = "Anatomy",
        title = "The bones of two hands and two feet added up",
        originalTitle = "Bones of the limbs",
        author = "Anatomists of the period",
        year = 1316,
        summary = "A human hand has 27 bones and a foot has 26, and a short program adds the bones of two hands and two feet.",
        details = "The count follows the standard description of the adult skeleton, in which the hand holds carpals, metacarpals and phalanges and the foot holds tarsals, metatarsals and phalanges. The example prints the bones of the two hands, the two feet and all four limbs together.",
        instructions = """
            1. Save the example as bones.py.
            2. Run it: python3 bones.py
        """,
        example = """
            HAND = 27
            FOOT = 26
            print("two hands:", 2 * HAND)
            print("two feet:", 2 * FOOT)
            print("two hands and two feet:", 2 * HAND + 2 * FOOT)
        """,
    ),

    entry(
        databaseId = 73,
        kind = Kind.EXAMPLE,
        section = "Water",
        title = "Paddles lifted by a chain pump in an hour",
        originalTitle = "Chain pump paddles",
        author = "Makers of chain pumps",
        year = 1313,
        summary = "A chain pump lifts water on paddles that pass round a wheel, and a short program counts the paddles lifted in an hour with illustrative figures.",
        details = "The figures in the example are illustrative: twenty paddles on the chain and three turns of the wheel in a minute, so the pump lifts 3600 paddles in an hour. The example is an exercise in multiplication and does not record the output of any real pump.",
        instructions = """
            1. Save the example as chain_pump.py.
            2. Run it: python3 chain_pump.py
        """,
        example = """
            PADDLES = 20
            TURNS_PER_MINUTE = 3
            print("paddles per turn:", PADDLES)
            print("paddles lifted in an hour:", PADDLES * TURNS_PER_MINUTE * 60)
        """,
    ),

    entry(
        databaseId = 73,
        kind = Kind.EXAMPLE,
        section = "Metal",
        title = "Strokes of a trip hammer in ten minutes",
        originalTitle = "Trip hammer strokes",
        author = "Makers of trip hammers",
        year = 1313,
        summary = "A trip hammer lifts its head once for each cam that passes the lifting arm, and a short program counts the strokes in ten minutes with illustrative figures.",
        details = "The figures in the example are illustrative: four cams on the wheel and twelve turns in a minute, so the hammer strikes 480 times in ten minutes. The example is an exercise in multiplication and does not record the speed of any real hammer.",
        instructions = """
            1. Save the example as hammer.py.
            2. Run it: python3 hammer.py
        """,
        example = """
            CAMS = 4
            TURNS_PER_MINUTE = 12
            print("strokes per turn:", CAMS)
            print("strokes in ten minutes:", CAMS * TURNS_PER_MINUTE * 10)
        """,
    ),

    entry(
        databaseId = 73,
        kind = Kind.EXAMPLE,
        section = "Numbers",
        title = "The years of the decade that are multiples of three",
        originalTitle = "Multiples of three",
        author = "No single author",
        year = 1311,
        summary = "Four years of the decade from 1310 to 1320 are multiples of three, and a short program lists them and counts them.",
        details = "The years are 1311, 1314, 1317 and 1320, and the program finds them by testing the remainder on division by three. The example prints the list and the count, which can be checked by dividing each year by three.",
        instructions = """
            1. Save the example as multiples.py.
            2. Run it: python3 multiples.py
        """,
        example = """
            years = range(1310, 1321)
            multiples = [year for year in years if year % 3 == 0]
            print("multiples of three:", " ".join(str(year) for year in multiples))
            print("count:", len(multiples))
        """,
    ),

)
