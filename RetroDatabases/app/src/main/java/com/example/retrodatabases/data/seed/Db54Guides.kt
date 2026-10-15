package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 1500 to 1510: Fibonacci ratios and the divine proportion, apothecaries' weights, brick courses, a dictionary's order and the cases of a Latin noun. */
val db54Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 54,
        kind = Kind.GUIDE,
        section = "Proportion",
        title = "How the divine proportion comes from Fibonacci ratios",
        originalTitle = "De divina proportione",
        author = "Luca Pacioli",
        year = 1509,
        summary = "Ratios of neighbouring Fibonacci numbers settle towards the divine proportion that Pacioli praised in 1509, and a short program prints six of them.",
        details = "Pacioli's book of 1509 praised the golden ratio, and the ratios of neighbouring numbers in the Fibonacci sequence approach it as the sequence grows. The example prints six ratios with four decimals, and the numbers are fixed, so the output never changes.",
        instructions = """
            1. Save the example as divine_ratio.py.
            2. Run it: python3 divine_ratio.py
        """,
        example = """
            fib = [1, 1]
            while len(fib) < 8:
                fib.append(fib[-1] + fib[-2])
            print("fibonacci:", fib)
            for n in range(1, 7):
                ratio = fib[n] / fib[n - 1]
                print(f"{fib[n]}/{fib[n - 1]} = {ratio:.4f}")
        """,
    ),

    entry(
        databaseId = 54,
        kind = Kind.GUIDE,
        section = "Medicine",
        title = "How apothecaries weigh a dose in grains and drachms",
        originalTitle = "Apothecaries' weights",
        author = "Apothecaries",
        year = 1500,
        summary = "Apothecaries weighed a dose in ounces, drachms and scruples, and a short program turns three doses into grains.",
        details = "An ounce was eight drachms, a drachm was three scruples and a scruple was twenty grains, so every unit is a fixed multiple of the grain. The three doses are illustrative, and the output is fixed.",
        instructions = """
            1. Save the example as apothecary_grains.py.
            2. Run it: python3 apothecary_grains.py
        """,
        example = """
            # An ounce is 8 drachms, a drachm is 3 scruples and a scruple is 20 grains, so an ounce is 480 grains.
            def grains(ounces, drachms, scruples):
                return ounces * 480 + drachms * 60 + scruples * 20

            doses = [(2, 5, 1), (0, 1, 2), (0, 3, 0)]
            for ounces, drachms, scruples in doses:
                print(f"{ounces} oz {drachms} dr {scruples} sc = {grains(ounces, drachms, scruples)} grains")
        """,
        rare = true,
    ),

    entry(
        databaseId = 54,
        kind = Kind.GUIDE,
        section = "Building",
        title = "How the courses of a brick wall add up to its height",
        originalTitle = "Brick courses",
        author = "Masons of the period",
        year = 1500,
        summary = "A brick wall is built in courses of a fixed height with their mortar, and a short program counts the full courses in three walls and the height left over.",
        details = "Masons of the period counted the courses of a wall to set its height, and a wall that is not an exact multiple of the course leaves a remainder. The heights and the course size are illustrative, so the output is fixed and can be checked by hand.",
        instructions = """
            1. Save the example as brick_courses.py.
            2. Run it: python3 brick_courses.py
        """,
        example = """
            # Heights in millimetres; a course of brick with its mortar is 90 millimetres high.
            COURSE = 90
            for height in [1800, 1850, 2250]:
                courses, left = divmod(height, COURSE)
                print(f"wall {height} mm: {courses} courses, {left} mm left over")
        """,
    ),

    entry(
        databaseId = 54,
        kind = Kind.GUIDE,
        section = "Dictionaries",
        title = "How a Latin dictionary sorts its headwords",
        originalTitle = "Dictionarium",
        author = "Ambrogio Calepino",
        year = 1502,
        summary = "A dictionary is a list in alphabetical order, so a short program sorts six Latin headwords and numbers them in that order.",
        details = "Calepino's Dictionarium of 1502 was a large alphabetical list of Latin words, printed at Reggio. The example sorts a small list of headwords by their letters, which is the order a reader expects in any dictionary.",
        instructions = """
            1. Save the example as sort_headwords.py.
            2. Run it: python3 sort_headwords.py
        """,
        example = """
            headwords = ["vox", "amor", "bonus", "cena", "dominus", "aqua"]

            for position, word in enumerate(sorted(headwords), start=1):
                print(position, word)
        """,
    ),

    entry(
        databaseId = 54,
        kind = Kind.GUIDE,
        section = "Grammar",
        title = "How a Latin noun is declined in the singular",
        originalTitle = "Latin grammar in school use",
        author = "Renaissance grammar schools",
        year = 1500,
        summary = "A first-declension Latin noun takes a different ending for each case, and a short program prints the six singular cases of the noun rosa, a rose.",
        details = "School grammars of the period taught the first declension, and rosa, a rose, is the usual model. The example adds each case ending to the stem ros, so the six forms are fixed and can be checked against any grammar.",
        instructions = """
            1. Save the example as rosa_cases.py.
            2. Run it: python3 rosa_cases.py
        """,
        example = """
            stem = "ros"
            endings = [("nominative", "a"), ("genitive", "ae"), ("dative", "ae"),
                       ("accusative", "am"), ("ablative", "a"), ("vocative", "a")]
            for case, ending in endings:
                print(case, stem + ending)
        """,
    ),

)
