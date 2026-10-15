package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 1650 to 1660: a parish register tally, a word index for a picture book, a cargo check under the Navigation Act and a deposit ledger that refuses overdrafts. */
val db39Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 39,
        kind = Kind.PROJECT,
        section = "Records",
        title = "A parish register tally for three years",
        originalTitle = "A parish register tally for three years",
        author = "Parliament of the Commonwealth",
        year = 1653,
        summary = "A program counts the births, marriages and burials in a parish register year by year, the kind of tally that the registration act of 1653 asked for.",
        details = "The entries in the example are invented, and the program groups them by year and by kind of record. Its totals check that every entry is counted once, which is the first duty of a clerk keeping a register.",
        instructions = """
            1. Save the example as register_tally.py.
            2. Run it: python3 register_tally.py
        """,
        example = """
            from collections import Counter

            ENTRIES = [
                (1654, "birth"), (1654, "burial"), (1654, "marriage"),
                (1655, "birth"), (1655, "birth"), (1655, "burial"),
                (1656, "marriage"), (1656, "burial"), (1656, "burial"),
            ]

            counts = Counter(ENTRIES)
            for year in sorted({y for y, _ in ENTRIES}):
                print(f"{year}: births {counts[(year, 'birth')]}, marriages {counts[(year, 'marriage')]}, burials {counts[(year, 'burial')]}")
            total = Counter(kind for _, kind in ENTRIES)
            print("totals: births", total["birth"], "marriages", total["marriage"], "burials", total["burial"])
        """,
    ),

    entry(
        databaseId = 39,
        kind = Kind.PROJECT,
        section = "Picture books",
        title = "A word index for a Comenius-style picture book",
        originalTitle = "Orbis sensualium pictus",
        author = "Johann Amos Comenius",
        year = 1658,
        summary = "A program builds an index that lists, for each word, the numbered pictures in which it appears, as a reader of a picture book would want.",
        details = "The example uses three invented pictures with a few words each, and it prints the index in alphabetical order. An index like this is what a teacher would use to find a word's picture quickly.",
        instructions = """
            1. Save the example as picture_index.py.
            2. Run it: python3 picture_index.py
        """,
        example = """
            PICTURES = {
                1: ["god", "heaven", "earth"],
                2: ["sun", "moon", "star"],
                3: ["man", "woman", "child"],
            }

            index = {}
            for number, words in PICTURES.items():
                for word in words:
                    index.setdefault(word, []).append(number)

            for word in sorted(index):
                print(word, index[word])
            print("words:", len(index), "pictures:", len(PICTURES))
        """,
    ),

    entry(
        databaseId = 39,
        kind = Kind.PROJECT,
        section = "Navigation",
        title = "Navigation Act cargo check of 1651",
        originalTitle = "Navigation Act",
        author = "Parliament of the Commonwealth",
        year = 1651,
        summary = "A program checks each cargo against the Navigation Act of 1651, which let goods reach England only in English ships or in ships of the country that made them.",
        details = "The shipments in the example are invented and are chosen to show each case of the rule. The program prints each decision and then counts the allowed and refused cargoes, so the outcome can be checked by hand.",
        instructions = """
            1. Save the example as cargo_check.py.
            2. Run it: python3 cargo_check.py
        """,
        example = """
            SHIPMENTS = [
                ("wine", "Portugal", "English"),
                ("spices", "Dutch", "Dutch"),
                ("timber", "Norwegian", "Dutch"),
                ("silk", "Italy", "Dutch"),
                ("tobacco", "English", "English"),
            ]

            def allowed(origin, ship_flag):
                # An English ship may carry any cargo; another ship may carry only goods made in its own country.
                return ship_flag == "English" or ship_flag == origin

            allowed_count = 0
            for goods, origin, ship in SHIPMENTS:
                ok = allowed(origin, ship)
                allowed_count += ok
                print(f"{goods} from {origin} on {ship} ship: {'allowed' if ok else 'refused'}")
            print(f"allowed {allowed_count}, refused {len(SHIPMENTS) - allowed_count}")
        """,
    ),

    entry(
        databaseId = 39,
        kind = Kind.PROJECT,
        section = "Banking",
        title = "A deposit ledger that refuses an overdraft",
        originalTitle = "Stockholms Banco",
        author = "Johan Palmstruch",
        year = 1656,
        summary = "A program keeps a running balance for one account, adding deposits, taking away withdrawals and refusing any withdrawal larger than the balance.",
        details = "The entries in the example are illustrative, and the balance after each line is printed so the ledger can be checked by hand. The refusal rule keeps the balance from ever going below zero.",
        instructions = """
            1. Save the example as ledger.py.
            2. Run it: python3 ledger.py
        """,
        example = """
            ENTRIES = [("deposit", 100), ("withdraw", 30), ("deposit", 50), ("withdraw", 120), ("withdraw", 10)]

            balance = 0
            for kind, amount in ENTRIES:
                if kind == "deposit":
                    balance += amount
                    print(f"deposit {amount}: balance {balance}")
                elif amount <= balance:
                    balance -= amount
                    print(f"withdraw {amount}: balance {balance}")
                else:
                    print(f"refused {amount}: balance {balance}")
        """,
    ),

)
