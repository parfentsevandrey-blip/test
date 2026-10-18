package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiments of 1283: the throws of three dice and the totals of two dice in the tables game, each in Python and C. */
val db76Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 76,
        kind = Kind.CROSS,
        section = "Dice",
        title = "Throws of three dice, counted in Python and C",
        originalTitle = "Libro de los juegos",
        author = "Alfonso X's court",
        year = 1283,
        summary = "Three dice give 216 ordered throws and 56 kinds of throw, and a script checks that the Python and C programs count the same totals.",
        details = "The programs count the ordered throws and the kinds of throw, and they print the counts for the totals seven and eight, which have 15 and 21 ordered throws. The run script compares the two outputs line by line.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### dice.py
            from itertools import product, combinations_with_replacement

            ordered = list(product(range(1, 7), repeat=3))
            kinds = list(combinations_with_replacement(range(1, 7), 3))
            print("ordered throws:", len(ordered))
            print("kinds of throw:", len(kinds))
            print("throws totalling 7:", sum(1 for t in ordered if sum(t) == 7))
            print("throws totalling 8:", sum(1 for t in ordered if sum(t) == 8))
            ### dice.c
            #include <stdio.h>

            int main(void) {
                int ordered = 0, kinds = 0, seven = 0, eight = 0;
                for (int a = 1; a <= 6; a++)
                    for (int b = 1; b <= 6; b++)
                        for (int c = 1; c <= 6; c++) {
                            ordered++;
                            if (a + b + c == 7) seven++;
                            if (a + b + c == 8) eight++;
                            if (a <= b && b <= c) kinds++;
                        }
                printf("ordered throws: %d\n", ordered);
                printf("kinds of throw: %d\n", kinds);
                printf("throws totalling 7: %d\n", seven);
                printf("throws totalling 8: %d\n", eight);
                return 0;
            }
            ### run.sh
            python3 dice.py > python.txt
            cc -o dice_c dice.c
            ./dice_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

    entry(
        databaseId = 76,
        kind = Kind.CROSS,
        section = "Dice",
        title = "Totals of two dice in the tables game, counted in Python and C",
        originalTitle = "Tablas",
        author = "The players of the tables game",
        year = 1283,
        summary = "Two dice give 36 ordered throws, and a script checks that the Python and C programs count the same number of throws for each total from 2 to 12.",
        details = "The tables game is played with two dice, and the totals from two to twelve come up with counts of 1, 2, 3, 4, 5, 6, 5, 4, 3, 2 and 1. The run script compares the two outputs line by line.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### totals.py
            counts = {}
            for a in range(1, 7):
                for b in range(1, 7):
                    counts[a + b] = counts.get(a + b, 0) + 1
            for total in range(2, 13):
                print(f"total {total}: {counts[total]}")
            print("throws:", sum(counts.values()))
            ### totals.c
            #include <stdio.h>

            int main(void) {
                int counts[13] = {0};
                for (int a = 1; a <= 6; a++)
                    for (int b = 1; b <= 6; b++)
                        counts[a + b]++;
                for (int total = 2; total <= 12; total++)
                    printf("total %d: %d\n", total, counts[total]);
                int all = 0;
                for (int total = 2; total <= 12; total++)
                    all += counts[total];
                printf("throws: %d\n", all);
                return 0;
            }
            ### run.sh
            python3 totals.py > python.txt
            cc -o totals_c totals.c
            ./totals_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
