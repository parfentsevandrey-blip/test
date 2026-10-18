package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 951: the poems of the Gosen wakashu shared out over its twenty volumes. */
val db109Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 109,
        kind = Kind.EXAMPLE,
        section = "Counts",
        title = "The poems of the Gosen wakashu shared out over its twenty volumes",
        originalTitle = "The poems of the Gosen wakashu shared out over its twenty volumes",
        author = "The compilers of the Gosen wakashu",
        year = 951,
        summary = "One count gives the Gosen wakashu 1,426 poems in twenty volumes, and a short program finds an average of 71.3 poems to a volume.",
        details = "The average is only arithmetic, since no count of any one volume is given here. The example also prints the poems left over after equal shares, so each line can be worked out by hand.",
        instructions = """
            1. Save the example as gosen_average.py.
            2. Run it: python3 gosen_average.py
        """,
        example = """
            poems = 1426
            volumes = 20
            print("poems:", poems)
            print("volumes:", volumes)
            print(f"average poems per volume: {poems / volumes:.1f}")
            print("left over after equal shares:", poems % volumes)
        """,
        rare = true,
    ),

)
