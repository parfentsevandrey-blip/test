package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 1530 to 1540: planetary magic squares, note patterns, rows of bricks, a rule of false position, a gelosia grid and an estate in twelfths. */
val db51Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 51,
        kind = Kind.EXAMPLE,
        section = "Magic squares",
        title = "Agrippa's planetary squares and their magic sums",
        originalTitle = "De occulta philosophia libri tres",
        author = "Cornelius Agrippa",
        year = 1533,
        summary = "Agrippa's squares for the planets have one magic sum for every row, column and diagonal, and a short program builds the odd-order squares and checks their sums.",
        details = "Agrippa's book of 1533 gives a square for each of the seven planets. The odd squares are built here by a stepping rule that is not the book's own method, and the example reports the magic sums of the squares for Saturn, Mars, Venus and the Moon, which are 15, 65, 175 and 369.",
        instructions = """
            1. Save the example as planet_squares.py.
            2. Run it: python3 planet_squares.py
        """,
        example = """
            PLANETS = [("Saturn", 3), ("Mars", 5), ("Venus", 7), ("Moon", 9)]

            def siamese(n):
                # Odd-order square: start at the middle of the top row, step up and to the right,
                # and step down instead when the cell is already filled.
                square = [[0] * n for _ in range(n)]
                row, col = 0, n // 2
                for value in range(1, n * n + 1):
                    square[row][col] = value
                    next_row, next_col = (row - 1) % n, (col + 1) % n
                    if square[next_row][next_col]:
                        next_row, next_col = (row + 1) % n, col
                    row, col = next_row, next_col
                return square

            for planet, n in PLANETS:
                square = siamese(n)
                magic = n * (n * n + 1) // 2
                lines = [sum(r) for r in square]
                lines += [sum(square[i][j] for i in range(n)) for j in range(n)]
                lines.append(sum(square[i][i] for i in range(n)))
                lines.append(sum(square[i][n - 1 - i] for i in range(n)))
                print(f"{planet}, order {n}: magic sum {magic}, lines equal: {all(s == magic for s in lines)}")
        """,
    ),

    entry(
        databaseId = 51,
        kind = Kind.EXAMPLE,
        section = "Music",
        title = "Ganassi's patterns that fill one long note",
        originalTitle = "La Fontegara",
        author = "Silvestro Ganassi",
        year = 1535,
        summary = "Ganassi's La Fontegara of 1535 taught patterns of shorter notes that fill one long note exactly, and a short program checks four such patterns with exact fractions.",
        details = "The book was printed at Venice in 1535, and its patterns divide a long note into shorter values, a practice that players called diminution. The example adds up the note values of four patterns and prints how many notes each contains, and every pattern fills the long note exactly.",
        instructions = """
            1. Save the example as note_patterns.py.
            2. Run it: python3 note_patterns.py
        """,
        example = """
            from fractions import Fraction as F

            PATTERNS = {
                "halves": (F(1, 2), F(1, 4), F(1, 4)),
                "quarters": (F(1, 4),) * 4,
                "eighths": (F(1, 8),) * 8,
                "dotted": (F(3, 8), F(3, 8), F(1, 8), F(1, 8)),
            }

            for name, notes in PATTERNS.items():
                total = sum(notes)
                print(f"pattern {name}: {len(notes)} notes, total {total}, fits: {total == 1}")
        """,
    ),

    entry(
        databaseId = 51,
        kind = Kind.EXAMPLE,
        section = "Arithmetic",
        title = "Triangular numbers counted as rows of bricks",
        originalTitle = "Triangular numbers",
        author = "Printers of the period",
        year = 1533,
        summary = "Rows of bricks that grow by one brick each time give the triangular numbers, and a short program adds six rows and checks each total with a formula.",
        details = "Rows of bricks that grow by one brick each time give the triangular numbers, a standard count in practical arithmetic. The example adds the rows one by one and checks each total with the formula n times n plus one, divided by two.",
        instructions = """
            1. Save the example as brick_rows.py.
            2. Run it: python3 brick_rows.py
        """,
        example = """
            total = 0
            for row in range(1, 7):
                total += row
                check = row * (row + 1) // 2
                print(f"row {row}: total {total}, formula {check}, agrees: {total == check}")
        """,
    ),

    entry(
        databaseId = 51,
        kind = Kind.EXAMPLE,
        section = "Arithmetic",
        title = "The rule of false position for a number and its parts",
        originalTitle = "Rule of false position",
        author = "Printers of the period",
        year = 1538,
        summary = "The rule of false position finds an unknown number from two trial values, and a short program solves for a number with its third and its quarter added.",
        details = "The rule of false position was a standard method of practical arithmetic, and it works two trial values through the same rule of parts. The example uses trial values of 12 and 24 and a target of 38, and it checks the answer by putting it back into the rule.",
        instructions = """
            1. Save the example as false_position.py.
            2. Run it: python3 false_position.py
        """,
        example = """
            from fractions import Fraction as F

            def f(x):
                # The number, with its third and its quarter added.
                return x + F(x, 3) + F(x, 4)

            TARGET = 38
            x1, x2 = F(12), F(24)                      # two trial values
            e1, e2 = f(x1) - TARGET, f(x2) - TARGET    # how far each trial misses the target
            print(f"trial {x1} gives {f(x1)}, trial {x2} gives {f(x2)}")
            answer = (x2 * e1 - x1 * e2) / (e1 - e2)
            print(f"the number is {answer}")
            print(f"check: f of the answer is {f(answer)}, equal to {TARGET}: {f(answer) == TARGET}")
        """,
    ),

    entry(
        databaseId = 51,
        kind = Kind.EXAMPLE,
        section = "Arithmetic",
        title = "The gelosia grid for multiplying two numbers",
        originalTitle = "Gelosia",
        author = "Printers of the period",
        year = 1540,
        summary = "The gelosia grid multiplies two numbers digit by digit, and a short program fills the grid for 23 times 47, adds the diagonals and prints the product.",
        details = "The grid method was taught in Italian arithmetic books of the fifteenth and sixteenth centuries, and its name gelosia refers to the lattice of the grid. The example adds each diagonal of the grid by place value and prints the product, which is 1081.",
        instructions = """
            1. Save the example as gelosia_grid.py.
            2. Run it: python3 gelosia_grid.py
        """,
        example = """
            a, b = 23, 47
            digits_a = [int(d) for d in str(a)]
            digits_b = [int(d) for d in str(b)]

            # Each cell of the grid holds the product of one digit of a and one digit of b;
            # cells on one diagonal share a place value.
            diagonals = [0] * (len(digits_a) + len(digits_b))
            for i, da in enumerate(digits_a):
                for j, db in enumerate(digits_b):
                    place = (len(digits_a) - 1 - i) + (len(digits_b) - 1 - j)
                    diagonals[place] += da * db

            product = sum(total * 10 ** place for place, total in enumerate(diagonals))
            print(f"diagonal sums by place value: {diagonals}")
            print(f"{a} x {b} = {product}")
        """,
    ),

    entry(
        databaseId = 51,
        kind = Kind.EXAMPLE,
        section = "Inheritance",
        title = "Shares of an estate reckoned in twelfths",
        originalTitle = "Estate divided into twelfths",
        author = "Printers of the period",
        year = 1534,
        summary = "An estate reckoned in twelfths can be shared among heirs by counting twelfths, and a short program checks that three shares of 3, 4 and 5 twelfths make the whole.",
        details = "Shares of an estate were often counted in twelfths, the Roman unit of twelve parts, and the portions in the example are invented for the calculation. The program prints each share as a fraction and confirms that the total is the whole estate.",
        instructions = """
            1. Save the example as estate_shares.py.
            2. Run it: python3 estate_shares.py
        """,
        example = """
            from fractions import Fraction as F

            shares = {"eldest son": F(3, 12), "second son": F(4, 12), "daughter": F(5, 12)}
            total = sum(shares.values())
            for heir, share in shares.items():
                print(f"{heir} receives {share} of the estate")
            print(f"total {total}, whole estate: {total == 1}")
        """,
    ),

)
