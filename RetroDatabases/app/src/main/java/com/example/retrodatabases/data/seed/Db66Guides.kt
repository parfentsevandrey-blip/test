package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 1380 to 1390: a tithe of sheaves, a rent shared by acres, a miller's toll, the octave of a feast and a widow's third. */
val db66Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 66,
        kind = Kind.GUIDE,
        section = "Tithes",
        title = "How a tithe is taken from a harvest of sheaves",
        originalTitle = "Tithe of the tenth sheaf",
        author = "Parish rectors and tithe collectors",
        year = 1386,
        summary = "A tithe takes every tenth sheaf of a harvest for the church, and a short program works out the tithe and the sheaves kept for three harvests.",
        details = "The tenth sheaf went to the church, and the example counts only whole tenths, so a harvest of nine sheaves gives none. Parish practice followed this rule across the period, and the example applies it to three harvests.",
        instructions = """
            1. Save the example as tithe.py.
            2. Run it: python3 tithe.py
        """,
        example = """
            def tithe(sheaves):
                given = sheaves // 10
                return given, sheaves - given

            for sheaves in [137, 40, 9]:
                given, kept = tithe(sheaves)
                print(f"{sheaves} sheaves: tithe {given}, kept {kept}")
        """,
    ),

    entry(
        databaseId = 66,
        kind = Kind.GUIDE,
        section = "Manors",
        title = "How a rent is shared among tenants in proportion to their acres",
        originalTitle = "Apportionment of rent",
        author = "Manorial stewards and their courts",
        year = 1386,
        summary = "A rent owed by a manor is shared among tenants in proportion to the acres they hold, and a short program shares a rent of 60 shillings among three tenants.",
        details = "Each share is the rent multiplied by a tenant's acres and divided by the total acres, and the example uses shares that come out whole so that the total is exact. Manorial courts divided rents this way, and the tenants and acres in the example are illustrative.",
        instructions = """
            1. Save the example as rent_shares.py.
            2. Run it: python3 rent_shares.py
        """,
        example = """
            RENT = 60
            ACRES = {"first tenant": 12, "second tenant": 8, "third tenant": 4}

            total = sum(ACRES.values())
            for name, acres in ACRES.items():
                print(f"{name} with {acres} acres pays {RENT * acres // total} shillings")
            print("total paid:", sum(RENT * acres // total for acres in ACRES.values()), "shillings")
        """,
    ),

    entry(
        databaseId = 66,
        kind = Kind.GUIDE,
        section = "Mills",
        title = "How a miller's toll is taken from a load of grain",
        originalTitle = "Multure",
        author = "Manorial millers and their lords",
        year = 1384,
        summary = "A miller kept one sixteenth of the grain he ground as his toll, and a short program takes the toll from three loads of grain.",
        details = "The toll was taken in whole sixteenths, so a load of fifteen bushels gives the miller nothing. The example applies the rule to three loads and prints what the miller took and what the owner kept.",
        instructions = """
            1. Save the example as miller_toll.py.
            2. Run it: python3 miller_toll.py
        """,
        example = """
            def toll(bushels):
                taken = bushels // 16
                return taken, bushels - taken

            for bushels in [337, 32, 15]:
                taken, kept = toll(bushels)
                print(f"{bushels} bushels: toll {taken}, kept {kept}")
        """,
    ),

    entry(
        databaseId = 66,
        kind = Kind.GUIDE,
        section = "Feasts",
        title = "How the octave day of a feast is counted",
        originalTitle = "Octave",
        author = "Church calendar makers",
        year = 1387,
        summary = "The octave of a feast falls on the eighth day counted from the feast, and a short program gives the octave day of three feasts.",
        details = "Church calendars counted an octave inclusively, so the octave day is seven days after the feast itself. The example gives the octave day of the feasts of Saint John the Baptist, Christmas and the Nativity of Mary.",
        instructions = """
            1. Save the example as octave.py.
            2. Run it: python3 octave.py
        """,
        example = """
            from datetime import date, timedelta

            feasts = [date(1386, 6, 24), date(1386, 12, 25), date(1386, 9, 8)]
            for feast in feasts:
                octave = feast + timedelta(days=7)
                print(f"feast {feast.day} {feast:%B}, octave {octave.day} {octave:%B}")
        """,
    ),

    entry(
        databaseId = 66,
        kind = Kind.GUIDE,
        section = "Inheritance",
        title = "How a widow's third is set aside before an estate is shared",
        originalTitle = "Widow's third",
        author = "Customary law courts",
        year = 1387,
        summary = "Customs of the period often gave a widow a third of an estate, and a short program divides an estate of 210 marks between a widow and four children.",
        details = "The example takes the third first and then shares the rest equally, and it shows that no remainder is left. Customs varied from place to place, so the example names one simple rule rather than a single law.",
        instructions = """
            1. Save the example as widows_third.py.
            2. Run it: python3 widows_third.py
        """,
        example = """
            ESTATE = 210
            CHILDREN = 4

            widow = ESTATE // 3
            rest = ESTATE - widow
            share = rest // CHILDREN
            print("widow's third:", widow)
            print("each of four children:", share)
            print("left over:", rest - share * CHILDREN)
        """,
    ),

)
