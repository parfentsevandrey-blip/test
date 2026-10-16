package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiments of 1340 and 1345: the chapter counts of three Chinese histories and a table of trade-route days, each in Python and in C. */
val db70Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 70,
        kind = Kind.CROSS,
        section = "Histories",
        title = "The chapter counts of three Chinese histories in Python and C",
        originalTitle = "Songshi, Liaoshi and Jinshi",
        author = "The Yuan court historians",
        year = 1345,
        summary = "Three dynastic histories have 496, 116 and 135 chapters, and a script checks that the Python and C programs print the same totals.",
        details = "The chapter counts are those usually given for the Songshi, Liaoshi and Jinshi, which the Yuan court compiled between 1343 and 1345. The run script compares the two outputs line by line, and the average is rounded down to whole chapters.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### histories.py
            CHAPTERS = {"Songshi": 496, "Liaoshi": 116, "Jinshi": 135}   # chapters of each history
            total = sum(CHAPTERS.values())
            for name in CHAPTERS:
                print(name, CHAPTERS[name])
            print("total chapters:", total)
            print("average chapters:", total // len(CHAPTERS))
            ### histories.c
            #include <stdio.h>

            int main(void) {
                const char *names[] = {"Songshi", "Liaoshi", "Jinshi"};
                int chapters[] = {496, 116, 135};   /* chapters of each history */
                int total = 0;
                for (int i = 0; i < 3; i++) {
                    printf("%s %d\n", names[i], chapters[i]);
                    total += chapters[i];
                }
                printf("total chapters: %d\n", total);
                printf("average chapters: %d\n", total / 3);
                return 0;
            }
            ### run.sh
            python3 histories.py > python.txt
            cc -o histories_c histories.c
            ./histories_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

    entry(
        databaseId = 70,
        kind = Kind.CROSS,
        section = "Trade",
        title = "Trade-route days between three ports as a table, in Python and C",
        originalTitle = "Pratica della mercatura",
        author = "Florentine merchants and their handbook",
        year = 1340,
        summary = "A table of voyage days between three ports is summed by row, and a script checks that the Python and C programs print the same totals.",
        details = "Pegolotti's handbook of about 1340 describes the trade of the Levant and the Black Sea, and the day counts in the table are illustrative and not taken from the handbook. The run script compares the two outputs line by line, so the row totals can be checked by hand.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### routes.py
            PORTS = ["Genoa", "Cyprus", "Tana"]   # three ports, with illustrative voyage days
            DAYS = [
                [0, 30, 60],
                [30, 0, 45],
                [60, 45, 0],
            ]
            for name, row in zip(PORTS, DAYS):
                print(name, sum(row))
            print("total days:", sum(sum(row) for row in DAYS))
            ### routes.c
            #include <stdio.h>

            int main(void) {
                const char *ports[] = {"Genoa", "Cyprus", "Tana"};
                int days[3][3] = {{0, 30, 60}, {30, 0, 45}, {60, 45, 0}};   /* illustrative voyage days */
                int grand = 0;
                for (int i = 0; i < 3; i++) {
                    int row = 0;
                    for (int j = 0; j < 3; j++) {
                        row += days[i][j];
                    }
                    printf("%s %d\n", ports[i], row);
                    grand += row;
                }
                printf("total days: %d\n", grand);
                return 0;
            }
            ### run.sh
            python3 routes.py > python.txt
            cc -o routes_c routes.c
            ./routes_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
