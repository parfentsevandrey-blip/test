package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiments of 1510 to 1518: Trithemius's progressive shift and Durer's magic square, each in Python and C. */
val db53Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 53,
        kind = Kind.CROSS,
        section = "Ciphers",
        title = "Trithemius's progressive shift in Python and C",
        originalTitle = "Polygraphiae libri sex",
        author = "Johannes Trithemius",
        year = 1518,
        summary = "A Python script and a C program both apply Trithemius's progressive shift to one word, and a run script checks that they print the same letters.",
        details = "Both programs use the same 24-sign table and the same rule, so the two outputs must match line by line. The message is illustrative, and the output can be checked by hand.",
        instructions = """
            1. Save the example as shift.py, shift.c and run.sh in one folder.
            2. Run it: sh run.sh
        """,
        example = """
            ### shift.py
            SIGNS = "ABCDEFGHIKLMNOPQRSTVXYZ&"

            def encipher(text):
                out = []
                for position, letter in enumerate(text):
                    out.append(SIGNS[(SIGNS.index(letter) + position) % len(SIGNS)])
                return "".join(out)

            print(encipher("LATIN"))
            ### shift.c
            #include <stdio.h>
            #include <string.h>

            static const char *SIGNS = "ABCDEFGHIKLMNOPQRSTVXYZ&";

            int main(void) {
                const char *text = "LATIN";
                size_t n = strlen(text);
                size_t count = strlen(SIGNS);
                for (size_t position = 0; position < n; position++) {
                    const char *p = strchr(SIGNS, text[position]);
                    size_t index = (size_t)(p - SIGNS);
                    putchar(SIGNS[(index + position) % count]);
                }
                putchar('\n');
                return 0;
            }
            ### run.sh
            python3 shift.py > python.txt
            cc -o shift_c shift.c
            ./shift_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

    entry(
        databaseId = 53,
        kind = Kind.CROSS,
        section = "Magic squares",
        title = "Durer's magic square check in Python and C",
        originalTitle = "Melencolia I",
        author = "Albrecht Durer",
        year = 1514,
        summary = "A Python script and a C program each print the row, column and diagonal sums of Durer's magic square, and a run script shows that both results match.",
        details = "The square of 1514 has every line adding to 34, so both programs print ten sums of 34 in the same order. The run script compares the two outputs line by line.",
        instructions = """
            1. Save the example as magic_square.py, magic_square.c and run.sh in one folder.
            2. Run it: sh run.sh
        """,
        example = """
            ### magic_square.py
            square = [[16, 3, 2, 13], [5, 10, 11, 8], [9, 6, 7, 12], [4, 15, 14, 1]]
            sums = [sum(row) for row in square] + [sum(col) for col in zip(*square)]
            sums += [sum(square[i][i] for i in range(4)), sum(square[i][3 - i] for i in range(4))]
            print(" ".join(str(s) for s in sums))
            ### magic_square.c
            #include <stdio.h>

            int main(void) {
                int square[4][4] = {
                    {16, 3, 2, 13},
                    {5, 10, 11, 8},
                    {9, 6, 7, 12},
                    {4, 15, 14, 1},
                };
                int sums[10];
                int count = 0;
                for (int r = 0; r < 4; r++) {
                    int total = 0;
                    for (int c = 0; c < 4; c++) total += square[r][c];
                    sums[count++] = total;
                }
                for (int c = 0; c < 4; c++) {
                    int total = 0;
                    for (int r = 0; r < 4; r++) total += square[r][c];
                    sums[count++] = total;
                }
                int main_diag = 0;
                int anti_diag = 0;
                for (int i = 0; i < 4; i++) {
                    main_diag += square[i][i];
                    anti_diag += square[i][3 - i];
                }
                sums[count++] = main_diag;
                sums[count++] = anti_diag;
                for (int i = 0; i < count; i++) {
                    if (i > 0) printf(" ");
                    printf("%d", sums[i]);
                }
                printf("\n");
                return 0;
            }
            ### run.sh
            python3 magic_square.py > python.txt
            cc -o magic_square_c magic_square.c
            ./magic_square_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
