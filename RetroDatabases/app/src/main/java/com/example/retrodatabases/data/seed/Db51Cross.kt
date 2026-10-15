package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiments of 1530 to 1540: Agrippa's planetary squares and the golden numbers of the 1530s, each in Python and C. */
val db51Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 51,
        kind = Kind.CROSS,
        section = "Magic squares",
        title = "Planetary magic sums in Python and C",
        originalTitle = "De occulta philosophia libri tres",
        author = "Cornelius Agrippa",
        year = 1533,
        summary = "The planetary squares of odd order are built by one stepping rule in Python and in C, and a script checks that both programs print the same magic sums.",
        details = "Agrippa's book of 1533 gives a square for each of the seven planets, and both programs build the odd-order squares with the same stepping rule. The run script compares the two outputs line by line.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### planet_squares.py
            PLANETS = [("Saturn", 3), ("Mars", 5), ("Venus", 7), ("Moon", 9)]

            def siamese(n):
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
            ### planet_squares.c
            #include <stdio.h>

            #define MAX 9

            static void siamese(int n, int square[MAX][MAX]) {
                for (int i = 0; i < n; i++)
                    for (int j = 0; j < n; j++)
                        square[i][j] = 0;
                int row = 0, col = n / 2;
                for (int value = 1; value <= n * n; value++) {
                    square[row][col] = value;
                    int next_row = (row - 1 + n) % n;
                    int next_col = (col + 1) % n;
                    if (square[next_row][next_col] != 0) {
                        next_row = (row + 1) % n;
                        next_col = col;
                    }
                    row = next_row;
                    col = next_col;
                }
            }

            int main(void) {
                const char *names[] = {"Saturn", "Mars", "Venus", "Moon"};
                int orders[] = {3, 5, 7, 9};
                for (int p = 0; p < 4; p++) {
                    int n = orders[p];
                    int square[MAX][MAX];
                    siamese(n, square);
                    int magic = n * (n * n + 1) / 2;
                    int equal = 1;
                    for (int i = 0; i < n; i++) {
                        int row_sum = 0, col_sum = 0;
                        for (int j = 0; j < n; j++) {
                            row_sum += square[i][j];
                            col_sum += square[j][i];
                        }
                        if (row_sum != magic || col_sum != magic) equal = 0;
                    }
                    int diag1 = 0, diag2 = 0;
                    for (int i = 0; i < n; i++) {
                        diag1 += square[i][i];
                        diag2 += square[i][n - 1 - i];
                    }
                    if (diag1 != magic || diag2 != magic) equal = 0;
                    printf("%s, order %d: magic sum %d, lines equal: %s\n", names[p], n, magic, equal ? "True" : "False");
                }
                return 0;
            }
            ### run.sh
            python3 planet_squares.py > python.txt
            cc -o planet_squares_c planet_squares.c
            ./planet_squares_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

    entry(
        databaseId = 51,
        kind = Kind.CROSS,
        section = "Calendars",
        title = "Golden numbers of 1530 to 1540 in Python and C",
        originalTitle = "Calendar tables with the golden number",
        author = "Printers of the period",
        year = 1530,
        summary = "The golden number of each year is the year modulo nineteen plus one, and a script checks that the Python and C programs print the same table for 1530 to 1540.",
        details = "Printed calendars of the period gave the golden number of each year, and both programs compute it with the same remainder rule. The run script compares the two outputs line by line.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### golden.py
            for year in range(1530, 1541):
                print(f"{year}: golden number {year % 19 + 1}")
            ### golden.c
            #include <stdio.h>

            int main(void) {
                for (int year = 1530; year <= 1540; year++) {
                    printf("%d: golden number %d\n", year, year % 19 + 1);
                }
                return 0;
            }
            ### run.sh
            python3 golden.py > python.txt
            cc -o golden_c golden.c
            ./golden_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
