package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiments of 1400 and 1409: a candle clock's rings and the weighted votes of the Kutna Hora decree, each in Python and C. */
val db64Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 64,
        kind = Kind.CROSS,
        section = "Time",
        title = "The rings of a candle clock in Python and C",
        originalTitle = "Candle clock",
        author = "Householders and monks of medieval Europe",
        year = 1400,
        summary = "A candle marked with rings shows the time by how far it has burned, and a script checks that the Python and C programs print the same clock time at each ring.",
        details = "The burning time between rings is illustrative: each ring marks thirty minutes, and the programs print the time at each of five rings. The run script compares the two outputs line by line.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### candle.py
            MINUTES_PER_RING = 30   # illustrative burning time between rings
            for ring in range(1, 6):
                minutes = ring * MINUTES_PER_RING
                print(f"ring {ring} at {minutes // 60} h {minutes % 60:02d} min")
            ### candle.c
            #include <stdio.h>

            int main(void) {
                const int minutes_per_ring = 30;   /* illustrative burning time between rings */
                for (int ring = 1; ring <= 5; ring++) {
                    int minutes = ring * minutes_per_ring;
                    printf("ring %d at %d h %02d min\n", ring, minutes / 60, minutes % 60);
                }
                return 0;
            }
            ### run.sh
            python3 candle.py > python.txt
            cc -o candle_c candle.c
            ./candle_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

    entry(
        databaseId = 64,
        kind = Kind.CROSS,
        section = "Universities",
        title = "Weighted votes of the Kutna Hora decree in Python and C",
        originalTitle = "Decretum Kuttenbergense",
        author = "King Wenceslas IV of Bohemia",
        year = 1409,
        summary = "Under the Kutna Hora decree the Bohemian nation had three votes and each other nation one, and a script checks that the Python and C programs count the same result.",
        details = "The example counts an illustrative set of votes with those weights, in Python and in C, and the run script compares the two outputs line by line. The votes are invented for the test, so the outcome says nothing about the real vote of 1409.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### weights.py
            WEIGHTS = {"Bohemian": 3, "Bavarian": 1, "Polish": 1, "Saxon": 1}   # votes under the decree
            YES = ["Bohemian", "Polish"]    # illustrative: the nations that voted yes
            NO = ["Bavarian", "Saxon"]      # illustrative: the nations that voted no
            yes = sum(WEIGHTS[nation] for nation in YES)
            no = sum(WEIGHTS[nation] for nation in NO)
            print("yes votes:", yes)
            print("no votes:", no)
            print("decided by:", "yes" if yes > no else "no")
            ### weights.c
            #include <stdio.h>

            int main(void) {
                /* votes under the decree: Bohemian 3, Bavarian 1, Polish 1, Saxon 1 */
                int bohemian = 3, bavarian = 1, polish = 1, saxon = 1;
                int yes = bohemian + polish;   /* illustrative: Bohemian and Polish voted yes */
                int no = bavarian + saxon;     /* illustrative: Bavarian and Saxon voted no */
                printf("yes votes: %d\n", yes);
                printf("no votes: %d\n", no);
                printf("decided by: %s\n", yes > no ? "yes" : "no");
                return 0;
            }
            ### run.sh
            python3 weights.py > python.txt
            cc -o weights_c weights.c
            ./weights_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
