package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 1300 to 1310: Llull's pairs of letters, a spinning wheel's drive ratio, the diopters of a spectacle lens, a trebuchet's counterweight and the catchwords of a quire. */
val db74Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 74,
        kind = Kind.GUIDE,
        section = "Logic",
        title = "How the nine letters of Llull's figures are paired in order",
        originalTitle = "Pairs of letters",
        author = "Ramon Llull",
        year = 1305,
        summary = "The nine letters that Llull uses for his basic concepts can be paired in order, and a short program lists the pairs and counts them.",
        details = "The letters are B, C, D, E, F, G, H, I and K, as in Llull's figures, and each pair keeps the earlier letter first. The example prints the number of pairs, which is 36, together with the first and last pair, so the count can be checked by hand.",
        instructions = """
            1. Save the example as llull_pairs.py.
            2. Run it: python3 llull_pairs.py
        """,
        example = """
            letters = ["B", "C", "D", "E", "F", "G", "H", "I", "K"]   # the nine letters of Llull's figures
            pairs = [(a, b) for i, a in enumerate(letters) for b in letters[i + 1:]]
            print("pairs:", len(pairs))
            print("first pair:", pairs[0][0], pairs[0][1])
            print("last pair:", pairs[-1][0], pairs[-1][1])
        """,
    ),

    entry(
        databaseId = 74,
        kind = Kind.GUIDE,
        section = "Textiles",
        title = "How a spinning wheel's drive ratio sets the bobbin turns",
        originalTitle = "Drive ratio of a spinning wheel",
        author = "Medieval spinners",
        year = 1300,
        summary = "The drive ratio of a spinning wheel is the wheel's diameter divided by the diameter of the bobbin pulley, and a short program turns that ratio into bobbin turns.",
        details = "The sizes in the example are illustrative: a wheel of 60 centimetres and a pulley of 2 centimetres give a ratio of 30. The example prints the bobbin turns for one turn of the wheel and for ten turns, so the arithmetic can be checked by hand.",
        instructions = """
            1. Save the example as spinning_ratio.py.
            2. Run it: python3 spinning_ratio.py
        """,
        example = """
            WHEEL_CM = 60   # diameter of the wheel, illustrative
            WHORL_CM = 2    # diameter of the bobbin pulley, illustrative
            ratio = WHEEL_CM // WHORL_CM
            print("bobbin turns per wheel turn:", ratio)
            print("bobbin turns in 10 wheel turns:", 10 * ratio)
        """,
    ),

    entry(
        databaseId = 74,
        kind = Kind.GUIDE,
        section = "Optics",
        title = "How the power in diopters of a spectacle lens is worked out",
        originalTitle = "Power in diopters",
        author = "Spectacle makers of Italy",
        year = 1306,
        summary = "The power of a spectacle lens in diopters is one hundred divided by its focal length in centimetres, and a short program prints the power for four lenses.",
        details = "The focal lengths are illustrative, and the example rounds each power to two places. A lens with a focal length of 50 centimetres has a power of 2 diopters, and the program prints each result so the division can be checked.",
        instructions = """
            1. Save the example as diopters.py.
            2. Run it: python3 diopters.py
        """,
        example = """
            focal_cm = [100, 50, 25, 40]   # focal lengths in centimetres, illustrative
            for f in focal_cm:
                print(f"focal {f} cm: {100 / f:.2f} diopters")
        """,
    ),

    entry(
        databaseId = 74,
        kind = Kind.GUIDE,
        section = "Siege",
        title = "How a trebuchet's counterweight balances its sling on the long arm",
        originalTitle = "Lever balance",
        author = "Siege engineers of the period",
        year = 1304,
        summary = "A trebuchet's beam balances when the counterweight times its short arm equals the load times the long arm, and a short program finds the load that balances a given counterweight.",
        details = "The numbers in the example are illustrative and do not describe the Warwolf. The program prints the balancing load and then checks both sides of the balance, so the result can be verified by hand.",
        instructions = """
            1. Save the example as lever_balance.py.
            2. Run it: python3 lever_balance.py
        """,
        example = """
            COUNTERWEIGHT_KG = 3000   # illustrative counterweight on the short arm
            SHORT_ARM_M = 1           # metres from the pivot to the counterweight
            LONG_ARM_M = 12           # metres from the pivot to the sling
            load_kg = COUNTERWEIGHT_KG * SHORT_ARM_M // LONG_ARM_M
            print("load that balances the counterweight, kg:", load_kg)
            print("check, counterweight moment:", COUNTERWEIGHT_KG * SHORT_ARM_M)
            print("check, load moment:", load_kg * LONG_ARM_M)
        """,
    ),

    entry(
        databaseId = 74,
        kind = Kind.GUIDE,
        section = "Manuscripts",
        title = "How catchwords tie the pages of a quire together",
        originalTitle = "Catchwords",
        author = "Scribes of the Latin West",
        year = 1300,
        summary = "A catchword written at the foot of each page repeats the first word of the next page, so a short program can check that the pages of a quire are in order.",
        details = "The example lists the first word of seven pages and the catchword on each, and it reports the one page whose catchword does not match. The sequence is made up for the check, and the rule itself is the one scribes used in the 1300s.",
        instructions = """
            1. Save the example as catchwords.py.
            2. Run it: python3 catchwords.py
        """,
        example = """
            pages = ["In", "Hic", "Sed", "Et", "Ut", "Quia", "Nam", "Ergo"]   # first word of each page
            catchwords = ["Hic", "Sed", "Et", "Ut", "Quia", "Nunc", "Ergo", "Tunc"]   # word at the foot of each page
            for i in range(len(pages) - 1):
                if catchwords[i] != pages[i + 1]:
                    print(f"page {i + 1}: catchword {catchwords[i]} but next page begins {pages[i + 1]}")
            print("pages checked:", len(pages) - 1)
        """,
    ),

)
