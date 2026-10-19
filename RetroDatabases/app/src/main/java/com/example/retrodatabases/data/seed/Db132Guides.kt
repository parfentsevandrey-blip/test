package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One guide of 720 to 730: the Tang units li, bu, chi and cun nested in fixed ratios, written out as code for the survey distances of 724. */
val db132Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 132,
        kind = Kind.GUIDE,
        section = "Units",
        title = "How the Tang units li, bu and chi nest, written out as code, the units in which the survey's distances were reckoned",
        originalTitle = "Li, bu and chi",
        author = "Tang measurers",
        year = 724,
        summary = "The Tang units li, bu and chi nest in fixed ratios, one li being 300 bu and one bu being 6 chi, so a length in li becomes chi by one multiplication.",
        details = "These ratios are the classical ones kept in the Tang, and the survey's distances are reckoned in li and bu. The sources do not date this unit system, so 724 is the year of the survey that used it, not the year of the units themselves.",
        rare = true,
        instructions = """
            1. Save the example as tang_units.py.
            2. Run it: python3 tang_units.py
        """,
        example = """
            BU_PER_LI = 300
            CHI_PER_BU = 6
            CUN_PER_CHI = 10

            chi_per_li = BU_PER_LI * CHI_PER_BU
            print("bu in one li:", BU_PER_LI)
            print("chi in one bu:", CHI_PER_BU)
            print("cun in one chi:", CUN_PER_CHI)
            print("chi in one li:", chi_per_li)
            print("cun in one li:", chi_per_li * CUN_PER_CHI)
            print("bu in one li, checked from chi:", chi_per_li // CHI_PER_BU)
        """,
    ),

)
