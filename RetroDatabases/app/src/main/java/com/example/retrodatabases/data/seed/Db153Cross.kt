package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One cross-code piece of 510 to 520: the 48 years of Zu Chongzhi's Daming calendar from its presentation in 462 to its approval in 510, in Python and in C. */
val db153Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 153,
        kind = Kind.CROSS,
        section = "Chinese",
        title = "The 48 years from Zu Chongzhi's Daming calendar of 462 to its approval in 510, counted in Python and in C",
        originalTitle = "Daming li",
        author = "Zu Chongzhi and his son Zu Geng",
        year = 510,
        summary = "A Python program and a C program both print the 48 years from the presentation of the Daming calendar in 462 to its approval in 510, and the 45 years that a 465 start gives.",
        details = "The 462 start follows the Chinese accounts, and 465 is the start that some accounts give, so the programs print both gaps and the six years of Zu Geng's campaign from 504. The two programs print the same lines, and a script checks that their outputs agree.",
        rare = true,
        instructions = """
            1. Save the example as daming_gap.py, daming_gap.c and run.sh in one folder.
            2. Run it: sh run.sh
        """,
        example = """
            ### daming_gap.py
            PRESENTED = 462      # the Daming calendar presented to the Liu Song court
            PRESENTED_ALT = 465  # the start that some accounts give
            APPROVED = 510       # the year the Liang court approved it
            CAMPAIGN = 504       # the year Zu Geng began to press for it
            print("from 462 to 510:", APPROVED - PRESENTED, "years")
            print("from 465 to 510:", APPROVED - PRESENTED_ALT, "years")
            print("campaign from 504 to 510:", APPROVED - CAMPAIGN, "years")
            ### daming_gap.c
            #include <stdio.h>

            int main(void) {
                int presented = 462, alt = 465, approved = 510, campaign = 504;
                printf("from 462 to 510: %d years\n", approved - presented);
                printf("from 465 to 510: %d years\n", approved - alt);
                printf("campaign from 504 to 510: %d years\n", approved - campaign);
                return 0;
            }
            ### run.sh
            python3 daming_gap.py > python.txt
            cc -o daming_gap_c daming_gap.c
            ./daming_gap_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
