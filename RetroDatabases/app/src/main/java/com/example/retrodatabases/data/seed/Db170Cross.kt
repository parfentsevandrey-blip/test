package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiments of 346 to 349: the years from the second attempt on Nisibis to the death of Shi Hu, in Python and C. */
val db170Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 170,
        kind = Kind.CROSS,
        section = "Dates",
        title = "Years from the second Persian attempt on Nisibis to the death of Shi Hu, in Python and C",
        originalTitle = "Years between two dates",
        author = "No single author",
        year = 349,
        summary = "The second Persian attempt on Nisibis came in 346 and the Later Zhao ruler Shi Hu died in 349 by the usual dating, and a script checks that the Python and C programs print the same three-year span.",
        details = "Shi Hu, ruler of the Later Zhao, is usually said to have died in 349, and the span is computed from the two years alone, so it says nothing about the causes of either event. The run script compares the two outputs line by line.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### span.py
            NISIBIS = 346   # the second Persian attempt on Nisibis, as dated here
            SHI_HU = 349    # the death of Shi Hu of the Later Zhao, as usually dated
            print("years from Nisibis to the death of Shi Hu:", SHI_HU - NISIBIS)
            ### span.c
            #include <stdio.h>

            int main(void) {
                int nisibis = 346;   /* the second Persian attempt on Nisibis, as dated here */
                int shi_hu = 349;    /* the death of Shi Hu of the Later Zhao, as usually dated */
                printf("years from Nisibis to the death of Shi Hu: %d\n", shi_hu - nisibis);
                return 0;
            }
            ### run.sh
            python3 span.py > python.txt
            cc -o span_c span.c
            ./span_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
        rare = true,
    ),

)
