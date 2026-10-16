package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 1480 to 1490: Ptolemy's degrees in stadia and miles, quire signatures, the lattice and quarter-square methods, and the long hundred. */
val db56Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 56,
        kind = Kind.GUIDE,
        section = "Geography",
        title = "How Ptolemy's degrees become stadia and miles",
        originalTitle = "Geographia",
        author = "Claudius Ptolemy",
        year = 1482,
        summary = "Ptolemy reckoned a degree of the earth as 500 stadia, and a short program turns degrees into stadia and into Roman miles.",
        details = "The Ulm edition of Ptolemy's Geography of 1482 lays its maps out by degrees, and accounts differ on which stadion Ptolemy meant, since the length of a stadion was not the same everywhere. The example uses the figure of 500 stadia to a degree and eight stadia to a Roman mile, both as commonly given, and prints four conversions.",
        instructions = """
            1. Save the example as ptolemy_miles.py.
            2. Run it: python3 ptolemy_miles.py
        """,
        example = """
            STADIA_PER_DEGREE = 500   # the figure commonly given for Ptolemy's degree
            STADIA_PER_MILE = 8       # a Roman mile of eight stadia, as commonly given

            for degrees in (1, 2, 10, 36):
                stadia = degrees * STADIA_PER_DEGREE
                miles = stadia / STADIA_PER_MILE
                print(f"{degrees} degrees: {stadia} stadia, {miles} Roman miles")
        """,
    ),

    entry(
        databaseId = 56,
        kind = Kind.GUIDE,
        section = "Printing",
        title = "How quire signatures label the gatherings of a book",
        originalTitle = "Signatures of printed quires",
        author = "Printers and binders",
        year = 1484,
        summary = "A printed book is folded into quires, and each quire carries a letter signature so that the binder can gather the sheets in order, and a short program prints the labels.",
        details = "The example uses an illustrative scheme that skips j, v and w and doubles the letters after the first run of the alphabet, since printers differed on which letters they left out. The labels it prints model the practice and are not the signatures of any named book.",
        instructions = """
            1. Save the example as signatures.py.
            2. Run it: python3 signatures.py
        """,
        example = """
            LETTERS = "abcdefghiklmnopqrstuxyz"   # 23 letters: this scheme omits j, v and w

            def signature(quire):
                index = quire - 1
                letter = LETTERS[index % len(LETTERS)]
                doubled = index // len(LETTERS) + 1
                return letter * doubled

            for quire in (1, 2, 3, 9, 10, 23, 24, 25):
                print(f"quire {quire}: signature {signature(quire)}")
        """,
    ),

    entry(
        databaseId = 56,
        kind = Kind.GUIDE,
        section = "Arithmetic",
        title = "How the lattice method multiplies two numbers",
        originalTitle = "Gelosia",
        author = "Italian arithmetic teachers",
        year = 1480,
        summary = "The lattice method, called gelosia in Italy after the grille of a window, multiplies two numbers digit by digit and then adds the diagonals of the grid.",
        details = "Italian arithmetic books of the period described the lattice under the name gelosia, which means a grille. The example fills the grid for 34 times 27, adds the cells along each diagonal and carries the sums to the final digits.",
        instructions = """
            1. Save the example as lattice.py.
            2. Run it: python3 lattice.py
        """,
        example = """
            a, b = 34, 27
            xs = [int(c) for c in str(a)][::-1]   # digits of a, units first
            ys = [int(c) for c in str(b)][::-1]   # digits of b, units first
            sums = [0] * (len(xs) + len(ys))
            for i, x in enumerate(xs):
                for j, y in enumerate(ys):
                    sums[i + j] += x * y          # each cell of the grid lands on the diagonal i + j
            print("diagonal sums:", sums)
            carry, digits = 0, []
            for total in sums:
                total += carry
                digits.append(str(total % 10))
                carry = total // 10
            print("digits from the right:", " ".join(digits))
            print(f"{a} x {b} = {int(''.join(reversed(digits)))}")
        """,
    ),

    entry(
        databaseId = 56,
        kind = Kind.GUIDE,
        section = "Trade",
        title = "How a long hundred of 120 counts goods",
        originalTitle = "Long hundred",
        author = "English merchants",
        year = 1485,
        summary = "The long hundred of 120 was a counting convention in English trade, and a short program turns long hundreds and dozens into pieces.",
        details = "Merchants counted many goods in long hundreds of 120 instead of the common hundred of 100, and a dozen of 12 pieces could be added to the count. The example converts three counts into pieces, to show how far the long hundred differs from the common one for the same number of goods.",
        instructions = """
            1. Save the example as long_hundred.py.
            2. Run it: python3 long_hundred.py
        """,
        example = """
            LONG_HUNDRED = 120   # the English long hundred, not the common hundred of 100
            DOZEN = 12

            for hundreds, dozens in ((1, 0), (2, 3), (5, 7)):
                pieces = hundreds * LONG_HUNDRED + dozens * DOZEN
                print(f"{hundreds} long hundred and {dozens} dozen: {pieces} pieces")
        """,
    ),

    entry(
        databaseId = 56,
        kind = Kind.GUIDE,
        section = "Arithmetic",
        title = "How the quarter-square method multiplies two numbers",
        originalTitle = "Quarter squares",
        author = "Arithmetic teachers, anonymous",
        year = 1480,
        summary = "The quarter-square method turns a product into the difference of two quarter squares, so a table of squares is enough to multiply two numbers.",
        details = "The method rests on the fact that the squares of the sum and the difference of two numbers differ by four times their product. The example applies it to three pairs of numbers, and the year given is approximate because the method was used by many arithmetic writers over several centuries.",
        instructions = """
            1. Save the example as quarter_squares.py.
            2. Run it: python3 quarter_squares.py
        """,
        example = """
            def quarter_square_product(a, b):
                s, d = a + b, abs(a - b)
                return (s * s) // 4 - (d * d) // 4

            for a, b in ((37, 23), (12, 9), (50, 3)):
                print(f"{a} x {b} = {quarter_square_product(a, b)}")
        """,
    ),

)
