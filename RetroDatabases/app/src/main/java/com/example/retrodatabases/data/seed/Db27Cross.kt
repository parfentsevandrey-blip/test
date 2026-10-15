package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiments of 1770 to 1780: a pin factory's output per worker and the knight's moves on a board, each in Python and C. */
val db27Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 27,
        kind = Kind.CROSS,
        section = "Division of labour",
        title = "Smith's pin factory in Python and C",
        originalTitle = "An Inquiry into the Nature and Causes of the Wealth of Nations",
        author = "Adam Smith",
        year = 1776,
        summary = "Adam Smith's figures for a pin factory are computed in Python and in C, and a script checks that both print the same output per worker.",
        details = "The Wealth of Nations of 1776 describes ten workers in a pin factory who together made upwards of forty-eight thousand pins a day, while one worker alone could hardly make twenty. The Python program divides the daily output among the workers and compares it with the single-worker figure, the C program repeats the same integer arithmetic, and the run script compares their outputs. The figures are Smith's own and are used here as he gave them.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### pin_factory.py
            pins_per_day = 48000     # Smith's figure for ten workers together, as he gave it
            workers = 10
            alone = 20               # Smith's upper figure for one worker alone

            per_worker = pins_per_day // workers
            print(f"pins per worker in the factory: {per_worker}")
            print(f"pins for one worker alone, at most: {alone}")
            print(f"ratio of output per worker: {per_worker // alone}")
            ### pin_factory.c
            #include <stdio.h>

            int main(void) {
                /* Smith's figures: 48000 pins a day for ten workers together, and 20 at most for one worker alone. */
                int pins_per_day = 48000;
                int workers = 10;
                int alone = 20;
                int per_worker = pins_per_day / workers;
                printf("pins per worker in the factory: %d\n", per_worker);
                printf("pins for one worker alone, at most: %d\n", alone);
                printf("ratio of output per worker: %d\n", per_worker / alone);
                return 0;
            }
            ### run.sh
            python3 pin_factory.py > python.txt
            cc -o pin_factory_c pin_factory.c
            ./pin_factory_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

    entry(
        databaseId = 27,
        kind = Kind.CROSS,
        section = "Board puzzles",
        title = "Knight's moves on a five by five board in Python and C",
        originalTitle = "Remarques sur les problemes de situation",
        author = "Alexandre-Theophile Vandermonde",
        year = 1771,
        summary = "The moves of a knight on a five by five board are counted in Python and in C, and a script checks that both print the same totals.",
        details = "Vandermonde's memoir on problems of situation of 1771 treated the paths of a knight across a board. The Python program counts the squares and the knight moves on a five by five board, the C program repeats the count with loops over the eight move offsets, and the run script compares their outputs. The total of 48 moves between squares is a check on the board's graph, not a result of the memoir.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### knight.py
            moves = [(1, 2), (2, 1), (2, -1), (1, -2), (-1, -2), (-2, -1), (-2, 1), (-1, 2)]
            n = 5
            squares = [(r, c) for r in range(n) for c in range(n)]

            def neighbours(r, c):
                return [(r + dr, c + dc) for dr, dc in moves if 0 <= r + dr < n and 0 <= c + dc < n]

            degrees = {s: len(neighbours(*s)) for s in squares}
            print("squares:", len(squares))
            print("knight moves, counted from each square:", sum(degrees.values()))
            print("moves between squares:", sum(degrees.values()) // 2)
            print("corner squares:", degrees[(0, 0)], "moves each")
            print("centre square:", degrees[(2, 2)], "moves")
            ### knight.c
            #include <stdio.h>

            int main(void) {
                /* The eight knight moves, as row and column offsets. */
                int dr[8] = {1, 2, 2, 1, -1, -2, -2, -1};
                int dc[8] = {2, 1, -1, -2, -2, -1, 1, 2};
                int n = 5;
                int total = 0;
                int corner = 0;
                int centre = 0;
                for (int r = 0; r < n; r++) {
                    for (int c = 0; c < n; c++) {
                        int degree = 0;
                        for (int k = 0; k < 8; k++) {
                            int rr = r + dr[k];
                            int cc = c + dc[k];
                            if (rr >= 0 && rr < n && cc >= 0 && cc < n) {
                                degree++;
                            }
                        }
                        total += degree;
                        if (r == 0 && c == 0) {
                            corner = degree;
                        }
                        if (r == 2 && c == 2) {
                            centre = degree;
                        }
                    }
                }
                printf("squares: %d\n", n * n);
                printf("knight moves, counted from each square: %d\n", total);
                printf("moves between squares: %d\n", total / 2);
                printf("corner squares: %d moves each\n", corner);
                printf("centre square: %d moves\n", centre);
                return 0;
            }
            ### run.sh
            python3 knight.py > python.txt
            cc -o knight_c knight.c
            ./knight_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
