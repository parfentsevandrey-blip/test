package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiment of 945: the chapter parts of the Old Book of Tang as shares of 200, counted in Python and in C. */
val db110Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 110,
        kind = Kind.CROSS,
        section = "Histories",
        title = "The chapter parts of the Old Book of Tang as shares of 200, in Python and C",
        originalTitle = "The chapter parts of the Old Book of Tang as shares of 200, in Python and C",
        author = "Liu Xu and the compilers of the Later Jin court",
        year = 945,
        summary = "Python and C programs compute the shares of annals, treatises and biographies in the Old Book of Tang's 200 chapters, and a script checks that both print 10, 15 and 75 percent.",
        details = "The C program does the same integer arithmetic with no date library, and the two outputs are compared with diff. The three parts are the usual 20, 30 and 150 chapters, so the run tests the arithmetic rather than the history.",
        instructions = """
            1. Save the example as tang_shares.py and tang_shares.c, and the run script as run.sh, in one folder.
            2. Run it: sh run.sh
        """,
        example = """
            ### tang_shares.py
            annals, treatises, biographies = 20, 30, 150
            total = annals + treatises + biographies
            for name, count in (("annals", annals), ("treatises", treatises), ("biographies", biographies)):
                print(name, count * 100 // total, "percent")
            ### tang_shares.c
            #include <stdio.h>

            int main(void) {
                int annals = 20, treatises = 30, biographies = 150;
                int total = annals + treatises + biographies;
                printf("annals %d percent\n", annals * 100 / total);
                printf("treatises %d percent\n", treatises * 100 / total);
                printf("biographies %d percent\n", biographies * 100 / total);
                return 0;
            }
            ### run.sh
            python3 tang_shares.py > python.txt
            cc -o tang_shares_c tang_shares.c
            ./tang_shares_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
