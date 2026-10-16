package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiments of 1490 to 1500: the eight-tone cycle of the Octoechos and a globe's grid, each in Python and C. */
val db55Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 55,
        kind = Kind.CROSS,
        section = "Octoechos",
        title = "The eight-tone cycle of the Octoechos in Python and C",
        originalTitle = "Oktoih prvoglasnik",
        author = "Durad Crnojevic",
        year = 1494,
        summary = "The eight tones of the Octoechos follow one another week by week, and a script in Python and one in C print the same tone for each of ten weeks.",
        details = "The Cetinje book of January 1494 is the Octoechos of the First Tone, and the example models the eight-tone cycle with a remainder. The run script compares the two outputs line by line, and both print the same ten weeks.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### tone.py
            for week in range(1, 11):
                tone = (week - 1) % 8 + 1
                print(f"week {week}: tone {tone}")
            ### tone.c
            #include <stdio.h>

            int main(void) {
                for (int week = 1; week <= 10; week++) {
                    int tone = (week - 1) % 8 + 1;
                    printf("week %d: tone %d\n", week, tone);
                }
                return 0;
            }
            ### run.sh
            python3 tone.py > python.txt
            cc -o tone_c tone.c
            ./tone_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

    entry(
        databaseId = 55,
        kind = Kind.CROSS,
        section = "Globes",
        title = "A globe's grid of meridians and parallels in Python and C",
        originalTitle = "Erdapfel",
        author = "Martin Behaim",
        year = 1492,
        summary = "A globe's grid of meridians and parallels can be counted for any step, and two programs in Python and C print the same counts for a fifteen-degree grid.",
        details = "The fifteen-degree grid is illustrative and does not describe the lines drawn on Behaim's globe. The run script compares the two outputs line by line.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### grid.py
            STEP = 15   # degrees between lines, illustrative
            meridians = list(range(0, 360, STEP))
            parallels = list(range(-90, 91, STEP))
            print("meridians:", len(meridians))
            print("parallels:", len(parallels))
            print("first parallels:", " ".join(str(p) for p in parallels[:3]))
            ### grid.c
            #include <stdio.h>

            int main(void) {
                int step = 15;
                printf("meridians: %d\n", 360 / step);
                printf("parallels: %d\n", 180 / step + 1);
                printf("first parallels:");
                for (int k = 0; k < 3; k++) {
                    printf(" %d", -90 + k * step);
                }
                printf("\n");
                return 0;
            }
            ### run.sh
            python3 grid.py > python.txt
            cc -o grid_c grid.c
            ./grid_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
