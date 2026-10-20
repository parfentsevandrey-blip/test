package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 300 to 310: how Porphyry's Enneads divide fifty-four treatises into six sets of nine, worked out in code. */
val db174Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 174,
        kind = Kind.GUIDE,
        section = "Numbering",
        title = "How Porphyry's Enneads divide fifty-four treatises into six sets of nine",
        originalTitle = "Enneads",
        author = "Porphyry of Tyre, editing Plotinus",
        year = 301,
        summary = "Porphyry's edition of Plotinus's treatises divides fifty-four of them into six sets of nine, and a short program shows the split and checks the total.",
        details = "Porphyry published the edition around 301, and the sets are called Enneads, from the Greek word for nine. The arrangement is by subject rather than by date, so the example does not reproduce the subjects, but it numbers the treatises in order and splits the list into sets of nine.",
        instructions = """
            1. Save the example as enneads.py.
            2. Run it: python3 enneads.py
        """,
        example = """
            TREATISES = 54
            PER_ENNEAD = 9
            enneads = TREATISES // PER_ENNEAD
            print("enneads:", enneads)
            for n in range(enneads):
                first = n * PER_ENNEAD + 1
                last = first + PER_ENNEAD - 1
                print("Ennead", n + 1, "treatises", first, "to", last)
            print("treatises checked:", enneads * PER_ENNEAD)
        """,
        rare = true,
    ),

)
