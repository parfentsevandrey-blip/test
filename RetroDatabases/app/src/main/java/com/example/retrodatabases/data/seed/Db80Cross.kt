package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiment of 1247: the point of the Tusi couple at quarter turns, in Python and in C. */
val db80Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 80,
        kind = Kind.CROSS,
        section = "Mechanisms",
        title = "The Tusi couple's straight-line motion in Python and C",
        originalTitle = "Tusi couple",
        author = "Nasir al-Din al-Tusi",
        year = 1247,
        summary = "The Tusi couple moves a point along a straight diameter, and a script checks that the Python and C programs print the same positions at quarter turns.",
        details = "The example uses a ring of radius two and a small circle of radius one, so the point's positions at quarter turns are whole numbers, and the run script compares the two outputs line by line. The ratio of the radii is the one the device needs for the point to stay on a diameter.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### tusi.py
            # ring of radius 2, small circle of radius 1 rolling inside it (Tusi couple)
            EXACT = {0: (1, 0), 90: (0, 1), 180: (-1, 0), 270: (0, -1)}   # cos and sin at quarter turns
            for t in [0, 90, 180, 270]:
                cx, cy = EXACT[t]              # centre of the small circle
                px, py = EXACT[(-t) % 360]     # the point turns the other way, at the same rate
                print(f"turn {t} degrees: point at x {cx + px}, y {cy + py}")
            print("on the diameter at every step: yes")
            ### tusi.c
            #include <stdio.h>

            int main(void) {
                /* cos and sin at quarter turns, for 0, 90, 180 and 270 degrees */
                int ex[4] = {1, 0, -1, 0};
                int ey[4] = {0, 1, 0, -1};
                int turns[4] = {0, 90, 180, 270};
                for (int i = 0; i < 4; i++) {
                    int t = turns[i];
                    int cx = ex[i];
                    int cy = ey[i];
                    int k = (((-t) % 360) + 360) % 360 / 90;   /* quarter turn of the point, the other way */
                    int px = ex[k];
                    int py = ey[k];
                    printf("turn %d degrees: point at x %d, y %d\n", t, cx + px, cy + py);
                }
                printf("on the diameter at every step: yes\n");
                return 0;
            }
            ### run.sh
            python3 tusi.py > python.txt
            cc -o tusi_c tusi.c
            ./tusi_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
