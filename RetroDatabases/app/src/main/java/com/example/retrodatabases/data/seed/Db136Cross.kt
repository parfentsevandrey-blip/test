package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One cross-code piece of 680 to 690: the years from the siege of Mecca in 683 to the Kota Kapur stone of 686, in Python and C. */
val db136Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 136,
        kind = Kind.CROSS,
        section = "Arabic and Old Malay",
        title = "Years from the siege of Mecca in 683 to the Kota Kapur stone of 686, counted in Python and C",
        originalTitle = "Mecca and Kota Kapur",
        author = "Husayn ibn Numayr and the court of Srivijaya",
        year = 683,
        summary = "The siege of Mecca in 683 and the Kota Kapur stone of 686 are three years apart, and a Python program and a C program print the same gap.",
        details = "The two dates are the usual years given for the siege and the stone, so the programs check the arithmetic, while the dates come from the sources on the two events. Accounts of the siege differ in their length, but the programs use the year 683 for it.",
        rare = true,
        instructions = """
            1. Save the example as mecca_kota.py, mecca_kota.c and run.sh in one folder.
            2. Run it: sh run.sh
        """,
        example = """
            ### mecca_kota.py
            MECCA = 683       # the siege of Mecca, as usually dated
            KOTA_KAPUR = 686  # the Kota Kapur stone, as usually dated
            print("years from the siege of Mecca to the Kota Kapur stone:", KOTA_KAPUR - MECCA)
            ### mecca_kota.c
            #include <stdio.h>

            int main(void) {
                int mecca = 683, kota_kapur = 686;
                printf("years from the siege of Mecca to the Kota Kapur stone: %d\n", kota_kapur - mecca);
                return 0;
            }
            ### run.sh
            python3 mecca_kota.py > python.txt
            cc -o mecca_kota_c mecca_kota.c
            ./mecca_kota_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
