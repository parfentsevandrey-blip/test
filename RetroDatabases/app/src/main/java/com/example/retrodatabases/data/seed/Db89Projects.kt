package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 1150 to 1160: stones for an arcade, panes for a church window, a monastery's bread and the rope for a row of wells, each with illustrative counts. */
val db89Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 89,
        kind = Kind.PROJECT,
        section = "Building",
        title = "Stones for the arches of a Romanesque arcade",
        originalTitle = "Arcade stones",
        author = "Masons of the twelfth century",
        year = 1155,
        summary = "A short program counts the wedge stones of the arches and the jamb stones of a Romanesque arcade, using illustrative counts for a nave of seven bays.",
        details = "The counts are illustrative: each arch uses eleven wedge stones and each bay has two jamb stones, and the program prints the totals so the stone order can be checked by hand. No real church is meant.",
        instructions = """
            1. Save the example as arcade.py.
            2. Run it: python3 arcade.py
        """,
        example = """
            BAYS = 7               # illustrative number of bays in the nave
            WEDGES_PER_ARCH = 11   # illustrative wedge stones in each arch
            JAMBS_PER_BAY = 2      # illustrative jamb stones beside each bay
            arch_stones = BAYS * WEDGES_PER_ARCH
            jamb_stones = BAYS * JAMBS_PER_BAY
            print("wedge stones in the arches:", arch_stones)
            print("jamb stones:", jamb_stones)
            print("total stones:", arch_stones + jamb_stones)
        """,
    ),

    entry(
        databaseId = 89,
        kind = Kind.PROJECT,
        section = "Glass",
        title = "Panes for the windows of a church with three lights",
        originalTitle = "Lancet window panes",
        author = "Glaziers of the twelfth century",
        year = 1152,
        summary = "A short program counts the glass panes needed for a set of lancet windows, each with three lights, and subtracts the panes lost to breakage.",
        details = "The counts are illustrative, and the program prints the panes needed and the panes left after breakage, so the stock of glass can be checked by hand. Stained glass of this period was cut in small pieces and held in lead, but the numbers here are not drawn from any church.",
        instructions = """
            1. Save the example as panes.py.
            2. Run it: python3 panes.py
        """,
        example = """
            WINDOWS = 5          # illustrative number of windows
            LIGHTS = 3           # lancet lights in each window
            PANES_PER_LIGHT = 8  # illustrative panes in each light
            BROKEN = 7           # illustrative panes lost to breakage
            needed = WINDOWS * LIGHTS * PANES_PER_LIGHT
            print("panes needed:", needed)
            print("panes left after breakage:", needed - BROKEN)
        """,
    ),

    entry(
        databaseId = 89,
        kind = Kind.PROJECT,
        section = "Monastery",
        title = "Loaves baked in a monastery kitchen for a week",
        originalTitle = "Monastic bread",
        author = "Monastic bakers",
        year = 1150,
        summary = "A short program totals the loaves a monastery kitchen bakes in a week and counts the oven batches they need, using illustrative numbers for monks and guests.",
        details = "The numbers are illustrative: forty monks and twelve guests each take one loaf a day, and an oven bakes sixty loaves a batch. The program rounds the batches up, so a partly filled batch counts as a full one.",
        instructions = """
            1. Save the example as bread.py.
            2. Run it: python3 bread.py
        """,
        example = """
            MONKS = 40              # illustrative community size
            GUESTS = 12             # illustrative guests each day
            DAYS = 7
            LOAVES_PER_BATCH = 60   # illustrative oven batch
            loaves = (MONKS + GUESTS) * DAYS
            batches = -(-loaves // LOAVES_PER_BATCH)   # rounded up
            print("loaves in the week:", loaves)
            print("oven batches:", batches)
        """,
    ),

    entry(
        databaseId = 89,
        kind = Kind.PROJECT,
        section = "Wells",
        title = "Rope lengths for the buckets of a row of wells",
        originalTitle = "Well ropes",
        author = "Well-keepers of the twelfth century",
        year = 1154,
        summary = "A short program adds the rope needed for each well, from its depth and an allowance at the top, and totals the rope for the whole row.",
        details = "The depths and the allowance are illustrative and measured in cubits, and the program prints each rope and the total so the figures can be checked by hand. The wells are not drawn from any real site.",
        instructions = """
            1. Save the example as wells.py.
            2. Run it: python3 wells.py
        """,
        example = """
            ALLOWANCE = 3                 # illustrative cubits left over at the top of each well
            depths = [18, 25, 31]         # illustrative well depths in cubits
            total = 0
            for number, depth in enumerate(depths, start=1):
                rope = depth + ALLOWANCE
                total += rope
                print("well", number, "needs", rope, "cubits of rope")
            print("total rope:", total, "cubits")
        """,
    ),

)
