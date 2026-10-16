package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 1310 to 1316: a three-field rotation, the solar terms, the five phases, the Mongol decimal army and a loom's threading draft, each written out as code. */
val db73Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 73,
        kind = Kind.GUIDE,
        section = "Farming",
        title = "How a three-field farm rotates its fallow field each year",
        originalTitle = "Three-field rotation",
        author = "Manorial farmers of northern Europe",
        year = 1310,
        summary = "A three-field manor rotated its fields so that one field lay fallow each year while the other two were sown, and a short program prints the rotation for four years.",
        details = "Each field lay fallow once in every three years, which spread the sowing over the whole manor. The example uses three named fields and the years 1310 to 1313, so the rotation can be checked by hand.",
        instructions = """
            1. Save the example as three_field.py.
            2. Run it: python3 three_field.py
        """,
        example = """
            FIELDS = ["north", "south", "east"]
            for year in range(1310, 1314):
                fallow = FIELDS[(year - 1310) % 3]
                sown = [field for field in FIELDS if field != fallow]
                print(year, "fallow:", fallow, "sown:", " and ".join(sown))
        """,
    ),

    entry(
        databaseId = 73,
        kind = Kind.GUIDE,
        section = "Calendar",
        title = "How the twenty-four solar terms fall two to a month",
        originalTitle = "Ershisi jieqi",
        author = "Chinese calendar makers",
        year = 1310,
        summary = "The twenty-four solar terms of the Chinese year come in pairs, and a short program gives each term its place in the list and the month in which it falls.",
        details = "The first term of each pair marks the start of its month and the second falls in the middle, so the list runs from lichun in February to dahan in January. The names are romanized so that the output stays in plain ASCII.",
        instructions = """
            1. Save the example as solar_terms.py.
            2. Run it: python3 solar_terms.py
        """,
        example = """
            TERMS = ["lichun", "yushui", "jingzhe", "chunfen", "qingming", "guyu",
                     "lixia", "xiaoman", "mangzhong", "xiazhi", "xiaoshu", "dashu",
                     "liqiu", "chushu", "bailu", "qiufen", "hanlu", "shuangjiang",
                     "lidong", "xiaoxue", "daxue", "dongzhi", "xiaohan", "dahan"]
            for index, term in enumerate(TERMS):
                month = (index // 2 + 1) % 12 + 1
                print(index + 1, term, "month", month)
            print("terms:", len(TERMS))
        """,
    ),

    entry(
        databaseId = 73,
        kind = Kind.GUIDE,
        section = "Theory",
        title = "How the five phases generate and overcome one another",
        originalTitle = "Wuxing",
        author = "Chinese cosmologists of the Yuan period",
        year = 1310,
        summary = "The five phases of Chinese thought, wood, fire, earth, metal and water, pass energy to one another in a cycle of generation and check one another in a cycle of overcoming, and a short program prints both cycles.",
        details = "Wood generates fire, fire earth, earth metal, metal water and water wood, while wood overcomes earth, earth water, water fire, fire metal and metal wood. The example prints both cycles for every phase, and the two cycles are kept in two tables.",
        instructions = """
            1. Save the example as five_phases.py.
            2. Run it: python3 five_phases.py
        """,
        example = """
            GENERATES = {"wood": "fire", "fire": "earth", "earth": "metal", "metal": "water", "water": "wood"}
            OVERCOMES = {"wood": "earth", "earth": "water", "water": "fire", "fire": "metal", "metal": "wood"}
            for phase in ["wood", "fire", "earth", "metal", "water"]:
                print(phase, "generates", GENERATES[phase], "and overcomes", OVERCOMES[phase])
            print("phases:", len(GENERATES))
        """,
    ),

    entry(
        databaseId = 73,
        kind = Kind.GUIDE,
        section = "Numbers",
        title = "How the Mongol decimal army counts its men by tens",
        originalTitle = "Arban, jaghun, mingghan, tumen",
        author = "The Mongol army of the Yuan",
        year = 1313,
        summary = "The Mongol army was organized in decimal units of ten, a hundred, a thousand and ten thousand men, and a short program splits a force of 2345 men into those units.",
        details = "The units were named arban for ten men, jaghun for a hundred, mingghan for a thousand and tumen for ten thousand. The example uses division with remainder, so the count of each unit and the men left over can be checked by adding them back up.",
        instructions = """
            1. Save the example as mongol_units.py.
            2. Run it: python3 mongol_units.py
        """,
        example = """
            SIZES = [("tumen", 10000), ("mingghan", 1000), ("jaghun", 100), ("arban", 10)]
            total = 2345
            rest = total
            for name, size in SIZES:
                count, rest = divmod(rest, size)
                print(name + ":", count)
            print("men left:", rest)
        """,
    ),

    entry(
        databaseId = 73,
        kind = Kind.GUIDE,
        section = "Weaving",
        title = "How a warp is threaded through the shafts of a loom",
        originalTitle = "Threading draft",
        author = "Weavers of the period",
        year = 1316,
        summary = "A warp is threaded through the shafts of a loom in a fixed order called the draft, and a short program prints the shaft for each thread of a four-shaft draft.",
        details = "In the four-shaft draft of the example, the threads run through shafts one to four in turn and then start again, so each shaft carries every fourth thread. The program prints the shaft of every thread and the count of threads on each shaft.",
        instructions = """
            1. Save the example as warp.py.
            2. Run it: python3 warp.py
        """,
        example = """
            SHAFTS = 4
            THREADS = 10
            counts = [0] * (SHAFTS + 1)
            for thread in range(1, THREADS + 1):
                shaft = (thread - 1) % SHAFTS + 1
                counts[shaft] += 1
                print("thread", thread, "on shaft", shaft)
            for shaft in range(1, SHAFTS + 1):
                print("shaft", shaft, "carries", counts[shaft], "threads")
        """,
    ),

)
