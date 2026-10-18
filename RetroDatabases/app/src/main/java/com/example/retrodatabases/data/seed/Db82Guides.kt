package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 1220: counting the syllables of a drottkvaett stanza, a Norse verse form set out in Snorri's Prose Edda. */
val db82Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 82,
        kind = Kind.GUIDE,
        section = "Verse",
        title = "How a drottkvaett stanza is counted in syllables",
        originalTitle = "Drottkvaett",
        author = "Skalds of Norway and Iceland",
        year = 1220,
        summary = "A drottkvaett stanza has eight lines of six syllables each, and a short program checks the syllables of each line of an illustrative stanza.",
        details = "Snorri's Hattatal, the list of metres at the end of his Prose Edda, sets out the rules of this court verse, including the count of six syllables in each line. The example uses an illustrative stanza, not a real one, so the count can be checked by hand line by line.",
        instructions = """
            1. Save the example as drottkvaett.py.
            2. Run it: python3 drottkvaett.py
        """,
        example = """
            STANZA = [
                "hal-di-ran-ga-mon-den",
                "sa-ri-to-ven-ka-bal",
                "ra-ma-ni-su-tor-gen",
                "ol-fe-rim-ka-dar-to",
                "ka-lu-me-ban-dro-sir",
                "ti-ver-na-ga-lun-da",
                "mo-ren-ba-tu-ka",
                "ha-sun-di-ka-ro-man",
            ]
            print("lines:", len(STANZA))
            six = 0
            for number, line in enumerate(STANZA, start=1):
                count = len(line.split("-"))
                if count == 6:
                    six += 1
                else:
                    print("line", number, "has", count, "syllables, not six")
            print("lines of six syllables:", six)
        """,
    ),

)
