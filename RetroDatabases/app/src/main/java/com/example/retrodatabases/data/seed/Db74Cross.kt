package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiments of 1300 to 1310: the triples of Llull's nine letters and a table of spectacle powers, each written in Python and in C. */
val db74Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 74,
        kind = Kind.CROSS,
        section = "Logic",
        title = "The triples of Llull's nine figures, counted by two programs",
        originalTitle = "Triples of letters",
        author = "Ramon Llull",
        year = 1305,
        summary = "The triples of Llull's nine letters are counted and listed by a Python program and a C program, and a run script checks that the two outputs agree.",
        details = "Nine letters give 84 triples, and the first and last triples in the order used by both programs are B C D and H I K. The run script compares the two outputs line by line.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### llull_triples.py
            from itertools import combinations

            letters = ["B", "C", "D", "E", "F", "G", "H", "I", "K"]
            triples = list(combinations(letters, 3))
            print("triples:", len(triples))
            print("first triple:", " ".join(triples[0]))
            print("last triple:", " ".join(triples[-1]))
            ### llull_triples.c
            #include <stdio.h>

            int main(void) {
                const char letters[] = "BCDEFGHIK";
                int n = 9, count = 0;
                char first[3] = {0}, last[3] = {0};
                for (int a = 0; a < n; a++)
                    for (int b = a + 1; b < n; b++)
                        for (int c = b + 1; c < n; c++) {
                            if (count == 0) { first[0] = letters[a]; first[1] = letters[b]; first[2] = letters[c]; }
                            last[0] = letters[a]; last[1] = letters[b]; last[2] = letters[c];
                            count++;
                        }
                printf("triples: %d\n", count);
                printf("first triple: %c %c %c\n", first[0], first[1], first[2]);
                printf("last triple: %c %c %c\n", last[0], last[1], last[2]);
                return 0;
            }
            ### run.sh
            python3 llull_triples.py > python.txt
            cc -o llull_c llull_triples.c
            ./llull_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

    entry(
        databaseId = 74,
        kind = Kind.CROSS,
        section = "Optics",
        title = "A table of spectacle diopters in Python and C",
        originalTitle = "Spectacle powers in integers",
        author = "Spectacle makers of Italy",
        year = 1306,
        summary = "A table of spectacle powers is printed by a Python program and a C program with integer arithmetic, and a run script checks that the two outputs agree.",
        details = "Powers are kept in hundredths of a diopter, so both programs print the same two decimal places without floating-point rounding. The focal lengths are illustrative, and the run script compares the two outputs line by line.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### spectacle_table.py
            focal_cm = [100, 50, 25, 40]
            for f in focal_cm:
                p = 10000 // f
                print(f"focal {f} cm: {p // 100}.{p % 100:02d} diopters")
            ### spectacle_table.c
            #include <stdio.h>

            int main(void) {
                int focal[] = {100, 50, 25, 40};
                for (int i = 0; i < 4; i++) {
                    int p = 10000 / focal[i];   /* hundredths of a diopter */
                    printf("focal %d cm: %d.%02d diopters\n", focal[i], p / 100, p % 100);
                }
                return 0;
            }
            ### run.sh
            python3 spectacle_table.py > python.txt
            cc -o spectacle_c spectacle_table.c
            ./spectacle_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
