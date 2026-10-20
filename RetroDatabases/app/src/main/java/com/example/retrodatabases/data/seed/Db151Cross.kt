package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One cross-code piece of 530 to 540: the years from the Endless Peace of 532 to the renewed war and to the Fifty Years' Peace of 562, in Python and in C. */
val db151Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 151,
        kind = Kind.CROSS,
        section = "Byzantine",
        title = "The years between the Endless Peace of 532 with Persia and the Fifty Years' Peace of 562, computed in Python and in C",
        originalTitle = "aionios eirene (Endless Peace)",
        author = "Justinian and Khosrow I",
        year = 532,
        summary = "A Python program and a C program print the years from the Endless Peace of 532 to the war that broke it in 540 and to the Fifty Years' Peace of 562.",
        details = "The Endless Peace between Justinian and Khosrow I was broken when Khosrow invaded Byzantine Syria in 540, and the Fifty Years' Peace of 562 closed the later wars, so the two programs print 8 years of peace and 30 years between the treaties. The terms of the treaties are not given here, and the arithmetic counts years between dates, not years in which the peace was kept.",
        rare = true,
        instructions = """
            1. Save the example as peace_years.py, peace_years.c and run.sh in one folder.
            2. Run it: sh run.sh
        """,
        example = """
            ### peace_years.py
            ENDLESS_PEACE = 532      # the Endless Peace between Justinian and Khosrow I
            WAR_RESUMED = 540        # Khosrow's invasion of Syria, which broke the peace
            FIFTY_YEARS_PEACE = 562  # the Fifty Years' Peace, signed later
            print("years from the Endless Peace to the renewed war:", WAR_RESUMED - ENDLESS_PEACE)
            print("years between the two peaces:", FIFTY_YEARS_PEACE - ENDLESS_PEACE)
            ### peace_years.c
            #include <stdio.h>

            int main(void) {
                int endless_peace = 532;     /* the Endless Peace between Justinian and Khosrow I */
                int war_resumed = 540;       /* Khosrow's invasion of Syria, which broke the peace */
                int fifty_years_peace = 562; /* the Fifty Years' Peace */
                printf("years from the Endless Peace to the renewed war: %d\n", war_resumed - endless_peace);
                printf("years between the two peaces: %d\n", fifty_years_peace - endless_peace);
                return 0;
            }
            ### run.sh
            python3 peace_years.py > python.txt
            cc -o peace_years_c peace_years.c
            ./peace_years_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
