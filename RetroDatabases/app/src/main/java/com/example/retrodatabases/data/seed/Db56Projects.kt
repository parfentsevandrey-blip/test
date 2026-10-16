package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 1480 to 1490: a running balance with signs, press-days for an edition, a library's volumes by shelf and spare sheets in a print run. */
val db56Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 56,
        kind = Kind.PROJECT,
        section = "Accounts",
        title = "A running balance with plus and minus signs",
        originalTitle = "Behende und hubsche Rechenung auff allen Kauffmanschafft",
        author = "Johannes Widmann",
        year = 1489,
        summary = "A running balance adds each entry with its own sign, plus for a gain and minus for a shortfall, and a short program prints the balance after every entry.",
        details = "Widmann's arithmetic of 1489 uses the plus and minus signs for surpluses and shortfalls in merchants' problems, and the balance here follows the same use. The entries are fixed in advance, so the balances the program prints can be checked by hand.",
        instructions = """
            1. Save the example as running_balance.py.
            2. Run it: python3 running_balance.py
        """,
        example = """
            entries = [4, -5, 10, -17]
            balance = 0
            for step in entries:
                balance += step
                sign = "+" if step >= 0 else "-"
                print(f"entry {sign}{abs(step)}: balance {balance}")
            print(f"final balance: {balance}")
        """,
    ),

    entry(
        databaseId = 56,
        kind = Kind.PROJECT,
        section = "Printing",
        title = "Press-days needed for an edition of 1200 copies",
        originalTitle = "Press schedule",
        author = "Printers of the 1480s",
        year = 1485,
        summary = "A press schedule multiplies the copies by the sheets in each copy and divides by what the press prints in a day, and a short program rounds the days up to whole days.",
        details = "All the figures in the example are illustrative and are not taken from any historical edition or press. The program shows the arithmetic a printer used to plan how many days a run would occupy the press.",
        instructions = """
            1. Save the example as press_days.py.
            2. Run it: python3 press_days.py
        """,
        example = """
            copies = 1200            # illustrative edition size
            sheets_per_copy = 40     # illustrative
            sheets_per_day = 600     # illustrative output of one press
            total_sheets = copies * sheets_per_copy
            press_days = -(-total_sheets // sheets_per_day)   # rounds up to whole days
            print(f"sheets to print: {total_sheets}")
            print(f"press-days needed: {press_days}")
        """,
    ),

    entry(
        databaseId = 56,
        kind = Kind.PROJECT,
        section = "Libraries",
        title = "Volumes on three shelves of a royal library",
        originalTitle = "Bibliotheca Corviniana",
        author = "Matthias Corvinus, king of Hungary",
        year = 1486,
        summary = "A shelf count adds the volumes on each shelf to give the total for a library, and a short program prints the count for each shelf and the sum.",
        details = "The counts in the example are invented for illustration and are not the holdings of the library at Buda. The program shows the tally a librarian needed when the shelves were counted for a list of the books.",
        instructions = """
            1. Save the example as shelf_count.py.
            2. Run it: python3 shelf_count.py
        """,
        example = """
            shelves = {"shelf A": 12, "shelf B": 7, "shelf C": 20}   # illustrative counts
            total = 0
            for name, volumes in shelves.items():
                total += volumes
                print(f"{name}: {volumes} volumes")
            print(f"total: {total} volumes")
        """,
    ),

    entry(
        databaseId = 56,
        kind = Kind.PROJECT,
        section = "Printing",
        title = "Sheets to print with spoiled and spare sheets",
        originalTitle = "Print run allowances",
        author = "Printers of the 1480s",
        year = 1488,
        summary = "A print run needs spare sheets for setting up the press and for spoiled sheets, and a short program adds these allowances to the copies ordered.",
        details = "Every figure in the example is illustrative, with one spoiled sheet allowed for each twenty-five copies and twenty sheets spent on setting up the press. The program prints the spoiled sheets and the total to print, to show how the allowances change the count.",
        instructions = """
            1. Save the example as spare_sheets.py.
            2. Run it: python3 spare_sheets.py
        """,
        example = """
            copies = 500
            spoiled_per_hundred = 4        # illustrative allowance for each hundred copies
            spare_for_make_ready = 20      # illustrative sheets spent setting up the press
            spoiled = copies // 100 * spoiled_per_hundred
            total = copies + spoiled + spare_for_make_ready
            print(f"copies: {copies}")
            print(f"spoiled sheets: {spoiled}")
            print(f"sheets to print: {total}")
        """,
    ),

)
