package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One cross-code piece of 580 to 590: the 500 provisions of the Kaihuang code divided among its twelve sections, in Python and in C. */
val db146Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 146,
        kind = Kind.CROSS,
        section = "Chinese",
        title = "The 500 provisions of the Kaihuang code divided among its twelve sections, computed in Python and in C",
        originalTitle = "Kaihuang lu",
        author = "Compilers of the Kaihuang code",
        year = 583,
        summary = "The Kaihuang code's 500 provisions, spread over twelve sections, come to 41 per section with 8 left over, as a Python program and a C program both print.",
        details = "The totals of twelve sections and 500 provisions are the usual figures for the code, and the division leaves 8 provisions over. The two programs must print the same lines, and the split is only arithmetic, not a record of how the provisions were grouped.",
        rare = true,
        instructions = """
            1. Save the example as kaihuang_split.py, kaihuang_split.c and run.sh in one folder.
            2. Run it: sh run.sh
        """,
        example = """
            ### kaihuang_split.py
            SECTIONS = 12       # the twelve sections of the Kaihuang code
            PROVISIONS = 500    # the provisions, as usually given
            print("sections:", SECTIONS, "provisions:", PROVISIONS)
            print("per section:", PROVISIONS // SECTIONS, "left over:", PROVISIONS % SECTIONS)
            ### kaihuang_split.c
            #include <stdio.h>

            int main(void) {
                int sections = 12, provisions = 500;
                printf("sections: %d provisions: %d\n", sections, provisions);
                printf("per section: %d left over: %d\n", provisions / sections, provisions % sections);
                return 0;
            }
            ### run.sh
            python3 kaihuang_split.py > python.txt
            cc -o kaihuang_split_c kaihuang_split.c
            ./kaihuang_split_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
