package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One cross-code experiment of 451: the reported bishop counts of Chalcedon, summed and ranked in Python and in C. */
val db159Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 159,
        kind = Kind.CROSS,
        section = "Councils",
        title = "The reported bishop counts of Chalcedon, summed and ranked in Python and in C",
        originalTitle = "Reported bishop counts",
        author = "No single author",
        year = 451,
        summary = "Five reported counts of the bishops at Chalcedon are summarised by the same program in Python and in C, and the two outputs are compared line by line.",
        details = "The counts differ because the reports measure different things, such as the bishops present at one time, the signatures and the figures given in letters and by Pope Leo, so the program measures the spread rather than settling a true number. The counts used are 520, 340, 600, 450 and 500, and both programs print the lowest, highest, median and mean.",
        instructions = """
            1. Save the example as bishops.py, bishops.c and run.sh in one folder.
            2. Run it: sh run.sh
        """,
        example = """
            ### bishops.py
            REPORTS = [520, 340, 600, 450, 500]   # reported counts of bishops, as listed
            ordered = sorted(REPORTS)
            total = sum(ordered)
            count = len(ordered)
            print("reports:", count)
            print("lowest:", ordered[0])
            print("highest:", ordered[-1])
            print("range:", ordered[-1] - ordered[0])
            print("median:", ordered[count // 2])
            print("mean:", total // count)
            ### bishops.c
            #include <stdio.h>
            #include <stdlib.h>

            static int by_size(const void *a, const void *b) {
                int x = *(const int *)a;
                int y = *(const int *)b;
                return (x > y) - (x < y);
            }

            int main(void) {
                int reports[] = {520, 340, 600, 450, 500};   /* reported counts, as listed */
                int count = (int)(sizeof reports / sizeof reports[0]);
                int total = 0;
                qsort(reports, count, sizeof reports[0], by_size);
                for (int i = 0; i < count; i++) {
                    total += reports[i];
                }
                printf("reports: %d\n", count);
                printf("lowest: %d\n", reports[0]);
                printf("highest: %d\n", reports[count - 1]);
                printf("range: %d\n", reports[count - 1] - reports[0]);
                printf("median: %d\n", reports[count / 2]);
                printf("mean: %d\n", total / count);
                return 0;
            }
            ### run.sh
            python3 bishops.py > python.txt
            cc -o bishops_c bishops.c
            ./bishops_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
        rare = true,
    ),

)
