package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 400 to 410: the monks needed for a quorum at an ordination under the Vinaya rules, with 402 as the placed anchor. */
val db164Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 164,
        kind = Kind.GUIDE,
        section = "Monastic law",
        title = "How many monks make a quorum for an ordination under the Vinaya count",
        originalTitle = "Vinaya quorum",
        author = "Compilers of the Vinaya rules",
        year = 402,
        summary = "The Vinaya rules ask for a quorum of monks at an ordination, ten in the central lands and five in the border lands, and a short program checks a count of monks against both figures.",
        details = "The rule is older than the year given, which is the anchor of this placed record: 402 is the year of Huiyuan's society on Mount Lu, not the date of the rule. The two figures, ten and five, are the ones usually given for the Pali Vinaya, and the counts in the example are illustrative rather than taken from a real assembly.",
        instructions = """
            1. Save the example as quorum.py.
            2. Run it: python3 quorum.py
        """,
        example = """
            CENTRAL_QUORUM = 10   # monks needed for an ordination in the central lands
            BORDER_QUORUM = 5     # monks needed for an ordination in the border lands
            for present in [3, 5, 9, 10, 123]:
                central = "yes" if present >= CENTRAL_QUORUM else "no"
                border = "yes" if present >= BORDER_QUORUM else "no"
                print(present, "monks: central quorum", central, "border quorum", border)
        """,
        rare = true,
    ),

)
