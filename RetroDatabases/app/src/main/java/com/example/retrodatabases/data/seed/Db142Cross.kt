package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One cross-code piece of 620 to 630: the years between the Wude code, Taizong's seizure of power and Bhaskara I's commentary, counted in Python and in C. */
val db142Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 142,
        kind = Kind.CROSS,
        section = "Chinese",
        title = "The years between the Wude code of 624, Taizong's seizure of power in 626 and Bhaskara I's commentary of 629, counted in Python and C",
        originalTitle = "The years between the Wude code of 624, Taizong's seizure of power in 626 and Bhaskara I's commentary of 629, counted in Python and C",
        author = "Various",
        year = 624,
        summary = "A Python program and a C program count the years between three dated events of 624 to 629, and a script checks that both print the same figures.",
        details = "The Wude code is dated 624, Taizong took power in 626 and Bhaskara I's commentary is dated 629, so the gaps are 2, 3 and 5 years. The two programs print the same figures, so the check confirms the arithmetic, while the dates come from the usual accounts of each item.",
        rare = true,
        instructions = """
            1. Save the example as years.py, years.c and run.sh in one folder.
            2. Run it: sh run.sh
        """,
        example = """
            ### years.py
            WUDE = 624     # the Wude code, as usually dated
            XUANWU = 626   # Taizong's seizure of power at the Xuanwu Gate
            BHASKARA = 629 # Bhaskara I's commentary on the Aryabhatiya
            print("from the Wude code to the Xuanwu Gate:", XUANWU - WUDE)
            print("from the Xuanwu Gate to the commentary:", BHASKARA - XUANWU)
            print("from the Wude code to the commentary:", BHASKARA - WUDE)
            ### years.c
            #include <stdio.h>

            int main(void) {
                int wude = 624, xuanwu = 626, bhaskara = 629;
                printf("from the Wude code to the Xuanwu Gate: %d\n", xuanwu - wude);
                printf("from the Xuanwu Gate to the commentary: %d\n", bhaskara - xuanwu);
                printf("from the Wude code to the commentary: %d\n", bhaskara - wude);
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
