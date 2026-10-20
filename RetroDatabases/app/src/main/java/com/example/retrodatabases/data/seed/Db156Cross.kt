package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiments of 480 to 490: the years from Zhang Qiujian's Mathematical Classic to Victor of Vita's history, in Python and C. */
val db156Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 156,
        kind = Kind.CROSS,
        section = "Years",
        title = "Years from the Mathematical Classic of Zhang Qiujian to Victor's Vandal history, in Python and C",
        originalTitle = "Zhang Qiujian suanjing and Historia persecutionis",
        author = "No single author",
        year = 485,
        summary = "A short program counts the years from Zhang Qiujian's Mathematical Classic of 485 to Victor of Vita's history of 488, and a run script checks that the Python and C programs print the same count.",
        details = "Both years are the ones used in this database, 485 for the end of the window given for the Mathematical Classic and 488 for the date used for Victor's history. The run script compares the two outputs line by line, so the check is on the two programs and not on the dates.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### years.py
            ZHANG_QIUJIAN = 485   # end of the usual range for the Mathematical Classic
            VICTOR = 488          # the date of Victor of Vita's history used here
            print("years from the Mathematical Classic to Victor's history:", VICTOR - ZHANG_QIUJIAN)
            ### years.c
            #include <stdio.h>

            int main(void) {
                const int zhang_qiujian = 485;   /* end of the usual range for the Mathematical Classic */
                const int victor = 488;          /* the date of Victor of Vita's history used here */
                printf("years from the Mathematical Classic to Victor's history: %d\n", victor - zhang_qiujian);
                return 0;
            }
            ### run.sh
            python3 years.py > python.txt
            cc -o years_c years.c
            ./years_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
