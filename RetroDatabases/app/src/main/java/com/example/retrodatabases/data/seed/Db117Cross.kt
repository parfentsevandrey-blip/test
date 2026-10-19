package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiment of 873: the spans of the Tahirid and Tulunid dynasties, counted in Python and C. */
val db117Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 117,
        kind = Kind.CROSS,
        section = "Khurasan and Egypt",
        title = "The spans of the Tahirid and Tulunid dynasties in Python and C",
        originalTitle = "The spans of the Tahirid and Tulunid dynasties in Python and C",
        author = "Tahir ibn al-Husayn and Ahmad ibn Tulun",
        year = 873,
        summary = "The Tahirid dynasty ruled Khurasan from 821 to 873 and the Tulunid dynasty ruled Egypt and Syria from 868 to 905, and a Python and a C program count each span and agree on the total.",
        details = "Both programs subtract the start year from the end year, so the run tests the two languages on the same arithmetic rather than on the history. The spans come out as 52 years for the Tahirids and 37 for the Tulunids, 89 years in all, and the dates are the ones usually given for each dynasty.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### spans.py
            TAHIRIDS = (821, 873)
            TULUNIDS = (868, 905)
            spans = [TAHIRIDS[1] - TAHIRIDS[0], TULUNIDS[1] - TULUNIDS[0]]
            print("Tahirids:", spans[0], "years")
            print("Tulunids:", spans[1], "years")
            print("together:", sum(spans), "years")
            ### spans.c
            #include <stdio.h>

            int main(void) {
                int tahirids = 873 - 821;
                int tulunids = 905 - 868;
                printf("Tahirids: %d years\n", tahirids);
                printf("Tulunids: %d years\n", tulunids);
                printf("together: %d years\n", tahirids + tulunids);
                return 0;
            }
            ### run.sh
            python3 spans.py > python.txt
            cc -o spans_c spans.c
            ./spans_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
