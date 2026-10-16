package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 1380 to 1390: Ming cash, English marks and nobles, Florentine pounds, Gascon wine, sheaves of arrows and the Jewish year. */
val db66Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 66,
        kind = Kind.EXAMPLE,
        section = "Money",
        title = "Ming copper cash counted in strings of a thousand coins",
        originalTitle = "Wen and guan",
        author = "Ming court and merchants",
        year = 1385,
        summary = "A string of Ming copper cash held a thousand coins, and a short program turns three sums of cash into coins.",
        details = "The example uses illustrative sums, and the totals show how strings and loose coins combine. The copper cash of the early Ming was strung in units of a thousand, so the string is the unit of account here.",
        instructions = """
            1. Save the example as ming_cash.py.
            2. Run it: python3 ming_cash.py
        """,
        example = """
            COINS_PER_STRING = 1000

            def to_coins(strings, coins):
                return strings * COINS_PER_STRING + coins

            amounts = [(12, 345), (3, 0), (0, 999)]
            for strings, coins in amounts:
                print(f"{strings} strings {coins} coins = {to_coins(strings, coins)} coins")
            print("total in coins:", sum(to_coins(*amount) for amount in amounts))
        """,
    ),

    entry(
        databaseId = 66,
        kind = Kind.EXAMPLE,
        section = "Money",
        title = "English money counted in marks, nobles and pence",
        originalTitle = "Mark and noble",
        author = "English exchequer clerks",
        year = 1388,
        summary = "A mark was 160 pence and a noble 80 pence, and a short program turns three sums of English money into pence.",
        details = "The sums are illustrative, and the totals show how marks, nobles and pence combine into one count. A mark was two thirds of a pound, and a noble was six shillings and eightpence, which is the basis of the conversion.",
        instructions = """
            1. Save the example as marks.py.
            2. Run it: python3 marks.py
        """,
        example = """
            PENCE_PER_MARK = 160
            PENCE_PER_NOBLE = 80

            def to_pence(marks, nobles, pence):
                return marks * PENCE_PER_MARK + nobles * PENCE_PER_NOBLE + pence

            sums = [(2, 1, 5), (0, 3, 0), (4, 0, 11)]
            for marks, nobles, pence in sums:
                print(f"{marks} marks {nobles} nobles {pence} pence = {to_pence(marks, nobles, pence)} pence")
            print("total in pence:", sum(to_pence(*amount) for amount in sums))
        """,
    ),

    entry(
        databaseId = 66,
        kind = Kind.EXAMPLE,
        section = "Weights",
        title = "Florentine pounds and ounces counted as once",
        originalTitle = "Libbra and oncia",
        author = "Florentine merchants",
        year = 1389,
        summary = "A Florentine libbra was twelve once, and a short program turns three weights into once.",
        details = "The weights are illustrative, and the example shows how the libbra and the once combine into one total. The units belong to the Florentine system of weight used in the trade of the period.",
        instructions = """
            1. Save the example as libbre.py.
            2. Run it: python3 libbre.py
        """,
        example = """
            ONCE_PER_LIBBRA = 12

            def to_once(libbre, once):
                return libbre * ONCE_PER_LIBBRA + once

            weights = [(9, 7), (2, 0), (0, 11)]
            for libbre, once in weights:
                print(f"{libbre} libbre {once} once = {to_once(libbre, once)} once")
            print("total in once:", sum(to_once(*weight) for weight in weights))
        """,
    ),

    entry(
        databaseId = 66,
        kind = Kind.EXAMPLE,
        section = "Measures",
        title = "Wine measures of tuns, pipes, hogsheads and gallons",
        originalTitle = "Tun of wine",
        author = "Wine merchants of Gascony and England",
        year = 1383,
        summary = "A tun of wine was two pipes, a pipe two hogsheads and a hogshead sixty-three gallons, and a short program converts three cargoes of wine into gallons.",
        details = "The cargoes are illustrative, and the totals show how the four units combine. The measures were used in the wine trade of the period, and each unit doubles or halves the one beside it.",
        instructions = """
            1. Save the example as wine_measures.py.
            2. Run it: python3 wine_measures.py
        """,
        example = """
            GALLONS_PER_HOGSHEAD = 63
            HOGSHEADS_PER_PIPE = 2
            PIPES_PER_TUN = 2

            def to_gallons(tuns, pipes, hogsheads, gallons):
                total_pipes = tuns * PIPES_PER_TUN + pipes
                total_hogsheads = total_pipes * HOGSHEADS_PER_PIPE + hogsheads
                return total_hogsheads * GALLONS_PER_HOGSHEAD + gallons

            cargoes = [(2, 1, 1, 5), (0, 3, 0, 0), (1, 0, 0, 0)]
            for tuns, pipes, hogsheads, gallons in cargoes:
                print(f"{tuns} tuns {pipes} pipes {hogsheads} hogsheads {gallons} gallons = {to_gallons(tuns, pipes, hogsheads, gallons)} gallons")
            print("total in gallons:", sum(to_gallons(*cargo) for cargo in cargoes))
        """,
    ),

    entry(
        databaseId = 66,
        kind = Kind.EXAMPLE,
        section = "Arms",
        title = "Arrows counted by the sheaf of twenty-four",
        originalTitle = "Sheaf of arrows",
        author = "Archers and the bowyers who supplied them",
        year = 1385,
        summary = "A sheaf of arrows was twenty-four arrows, and a short program turns three counts of arrows into single arrows.",
        details = "The counts are illustrative, and the example shows how the sheaf combines with loose arrows into one total. Archers bought and counted their arrows by the sheaf, so the unit appears in the accounts of the period.",
        instructions = """
            1. Save the example as arrows.py.
            2. Run it: python3 arrows.py
        """,
        example = """
            ARROWS_PER_SHEAF = 24

            def to_arrows(sheaves, arrows):
                return sheaves * ARROWS_PER_SHEAF + arrows

            counts = [(3, 7), (10, 0), (0, 23)]
            for sheaves, arrows in counts:
                print(f"{sheaves} sheaves {arrows} arrows = {to_arrows(sheaves, arrows)} arrows")
            print("total in arrows:", sum(to_arrows(*count) for count in counts))
        """,
    ),

    entry(
        databaseId = 66,
        kind = Kind.EXAMPLE,
        section = "Calendars",
        title = "The Jewish year count for 1380 to 1390 in anno mundi",
        originalTitle = "Anno mundi",
        author = "Jewish calendar makers",
        year = 1386,
        summary = "The Jewish year is counted from the creation, so a Common Era year gives one anno mundi number before the new year and a higher one after it, and a short program prints both for each year of the decade.",
        details = "The number is 3760 added to the Common Era year before the Jewish new year in autumn, and 3761 after it. The example uses 1 January and 31 December, which fall on either side of the new year in every year.",
        instructions = """
            1. Save the example as anno_mundi.py.
            2. Run it: python3 anno_mundi.py
        """,
        example = """
            for year in range(1380, 1391):
                print(f"{year}: 1 January {year + 3760}, 31 December {year + 3761}")
        """,
    ),

)
