package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One cross-code piece of 520 to 530: the 532-year Easter cycle of 28 solar years times 19 lunar years, in Python and in C. */
val db152Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 152,
        kind = Kind.CROSS,
        section = "Calendar",
        title = "The 532-year Easter cycle as 28 solar years times 19 lunar years, computed in Python and in C",
        originalTitle = "The 532-year Easter cycle as 28 solar years times 19 lunar years, computed in Python and in C",
        author = "Dionysius Exiguus",
        year = 525,
        summary = "The 28-year solar cycle times the 19-year lunar cycle gives the 532-year Easter cycle, and a Python program and a C program print the same count.",
        details = "Twenty-eight solar years and nineteen lunar years return to the same pairing after 532 years, the length of the Easter cycle that sixth-century reckoners worked with. Dionysius's table of 525 begins at AD 532, the first year of this count, so 525 is the year of that table and not the date when the cycle was devised.",
        rare = true,
        instructions = """
            1. Save the example as cycle_easter.py, cycle_easter.c and run.sh in one folder.
            2. Run it: sh run.sh
        """,
        example = """
            ### cycle_easter.py
            SOLAR = 28
            LUNAR = 19
            CYCLE = SOLAR * LUNAR
            print("cycle length:", CYCLE)
            print("first year:", 532, "last year:", 532 + CYCLE - 1)
            ### cycle_easter.c
            #include <stdio.h>

            int main(void) {
                int solar = 28;
                int lunar = 19;
                int cycle = solar * lunar;
                printf("cycle length: %d\n", cycle);
                printf("first year: %d last year: %d\n", 532, 532 + cycle - 1);
                return 0;
            }
            ### run.sh
            python3 cycle_easter.py > python.txt
            cc -o cycle_easter_c cycle_easter.c
            ./cycle_easter_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
