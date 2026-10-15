package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 1600 to 1610: a giro ledger, shelfmarks sorted as numbers, a spice cargo's value and the sheets of a book. */
val db44Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 44,
        kind = Kind.PROJECT,
        section = "Banking",
        title = "A giro ledger where every transfer balances",
        originalTitle = "Amsterdamsche Wisselbank",
        author = "The Amsterdam bank",
        year = 1609,
        summary = "Bank money moved by transfers between accounts, a debit on one account and a credit on another, so the total of all balances never changes, which a short program checks.",
        details = "Account holders of the Amsterdam bank settled payments by transfer in its books of 1609, and the program follows that bookkeeping. The balances and transfers are invented for the example, and the program prints the final balances and their total.",
        instructions = """
            1. Save the example as giro_ledger.py.
            2. Run it: python3 giro_ledger.py
        """,
        example = """
            balances = {"merchant A": 1000, "merchant B": 250, "bank reserve": 0}
            transfers = [("merchant A", "merchant B", 300), ("merchant B", "bank reserve", 100), ("merchant A", "bank reserve", 50)]

            for payer, payee, amount in transfers:
                # A transfer is a debit on one account and a credit on another, so the total of all balances never changes.
                balances[payer] -= amount
                balances[payee] += amount

            for name in sorted(balances):
                print(name, balances[name])
            print("total", sum(balances.values()))
        """,
    ),

    entry(
        databaseId = 44,
        kind = Kind.PROJECT,
        section = "Libraries",
        title = "Sorting Bodleian shelfmarks as numbers",
        originalTitle = "Catalogus librorum bibliothecae Bodleianae",
        author = "Thomas James",
        year = 1605,
        summary = "A shelfmark such as A.10.2 sorts after A.2.10 only when its numbers are read as numbers, which a short program does with a key function.",
        details = "The Bodleian shelved its books by marks of this kind, and James's catalogue of 1605 recorded them. The shelfmarks in the example are invented and are not Bodleian marks, and the program prints them in shelf order.",
        instructions = """
            1. Save the example as shelfmarks.py.
            2. Run it: python3 shelfmarks.py
        """,
        example = """
            SHELFMARKS = ["B.3.1", "A.10.2", "A.2.7", "C.1.1", "A.2.10"]

            def key(mark):
                # Split the mark at its dots, so that the numbers sort as numbers and not as text.
                letter, *numbers = mark.split(".")
                return (letter, [int(n) for n in numbers])

            for mark in sorted(SHELFMARKS, key=key):
                print(mark)
        """,
    ),

    entry(
        databaseId = 44,
        kind = Kind.PROJECT,
        section = "Trade",
        title = "The value of a pepper and spice cargo",
        originalTitle = "Vereenigde Oostindische Compagnie",
        author = "Dutch East India Company",
        year = 1602,
        summary = "A cargo's value is its units times the price per unit, and a short program totals the value of three goods in a spice cargo.",
        details = "The Dutch East India Company traded in pepper, cloves and nutmeg from the Indies, and its accounts valued each cargo in guilders. The units and prices in the example are invented for the calculation and are not the Company's figures.",
        instructions = """
            1. Save the example as cargo_value.py.
            2. Run it: python3 cargo_value.py
        """,
        example = """
            CARGO = [("pepper", 120, 9), ("cloves", 40, 30), ("nutmeg", 25, 22)]   # goods, units, guilders per unit; invented

            total = 0
            for goods, units, price in CARGO:
                value = units * price
                total += value
                print(f"{goods}: {units} units at {price} guilders, value {value}")
            print(f"cargo value: {total} guilders")
        """,
    ),

    entry(
        databaseId = 44,
        kind = Kind.PROJECT,
        section = "Printing",
        title = "Sheets for a 300-page book in three formats",
        originalTitle = "Folio, quarto and octavo",
        author = "Printers of the period",
        year = 1603,
        summary = "Folio, quarto and octavo name how many pages a printed sheet makes, and a program counts the sheets and blank pages a 300-page book needs in each format.",
        details = "A sheet folded once gives four pages, folded twice eight, and folded three times sixteen, and presses of the period worked in these formats. The 300-page book is an illustrative length, not a real one, and the program counts whole sheets only.",
        instructions = """
            1. Save the example as sheets.py.
            2. Run it: python3 sheets.py
        """,
        example = """
            import math

            FORMATS = {"folio": 4, "quarto": 8, "octavo": 16}   # pages printed on one sheet

            for name, pages_per_sheet in FORMATS.items():
                sheets = math.ceil(300 / pages_per_sheet)
                print(f"{name}: {sheets} sheets, {sheets * pages_per_sheet - 300} blank pages")
        """,
    ),

)
