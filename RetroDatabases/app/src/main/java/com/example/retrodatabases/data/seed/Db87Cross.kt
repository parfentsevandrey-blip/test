package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiment of 1170: the perfect consonances of a two-part organum, counted in Python and in C. */
val db87Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 87,
        kind = Kind.CROSS,
        section = "Music",
        title = "Perfect consonances in a two-part organum, counted in Python and C",
        originalTitle = "Organum consonances",
        author = "Singers and teachers of Notre-Dame",
        year = 1170,
        summary = "Perfect consonances in two-part organum are the unison, fourth, fifth and octave, and a script checks that the Python and C programs count the same perfect and imperfect points.",
        details = "The intervals in the example are illustrative and are given in semitones, so the unison is zero and the octave twelve. The run script compares the two outputs line by line.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### organum.py
            INTERVALS = [7, 4, 5, 12, 3, 0, 7]   # illustrative intervals between the two voices, in semitones
            PERFECT = {0, 5, 7, 12}              # unison, fourth, fifth and octave
            perfect = [i for i in INTERVALS if i in PERFECT]
            imperfect = [i for i in INTERVALS if i not in PERFECT]
            print("points:", len(INTERVALS))
            print("perfect consonances:", len(perfect))
            print("imperfect consonances:", len(imperfect))
            print("first imperfect point:", INTERVALS.index(imperfect[0]) + 1, "with", imperfect[0], "semitones")
            ### organum.c
            #include <stdio.h>

            int main(void) {
                int intervals[] = {7, 4, 5, 12, 3, 0, 7};   /* illustrative intervals, in semitones */
                int n = 7;
                int perfect = 0, imperfect = 0, first = 0, first_value = 0;
                for (int i = 0; i < n; i++) {
                    int p = intervals[i] == 0 || intervals[i] == 5 || intervals[i] == 7 || intervals[i] == 12;
                    if (p) {
                        perfect++;
                    } else {
                        imperfect++;
                        if (first == 0) {
                            first = i + 1;
                            first_value = intervals[i];
                        }
                    }
                }
                printf("points: %d\n", n);
                printf("perfect consonances: %d\n", perfect);
                printf("imperfect consonances: %d\n", imperfect);
                printf("first imperfect point: %d with %d semitones\n", first, first_value);
                return 0;
            }
            ### run.sh
            python3 organum.py > python.txt
            cc -o organum_c organum.c
            ./organum_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
