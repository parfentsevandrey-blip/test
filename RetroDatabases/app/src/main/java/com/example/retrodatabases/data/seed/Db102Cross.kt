package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiment of 1026: the positions of three notes in Guido's gamut, found by a Python program and a C program. */
val db102Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 102,
        kind = Kind.CROSS,
        section = "Music",
        title = "The positions of three notes in Guido's gamut, in Python and C",
        originalTitle = "The positions of three notes in Guido's gamut, in Python and C",
        author = "Guido of Arezzo",
        year = 1026,
        summary = "Two programs look up three note names in Guido's gamut of twenty notes and print the same positions in Python and in C.",
        details = "The gamut runs from the lowest gamma to the double e, twenty notes in all, and it is usually linked with Guido's teaching. The date is approximate, since the exact origin of the gamut is uncertain, and the run script checks that the two programs print the same lines.",
        rare = true,
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### gamut.py
            gamut = ["Gamma", "A", "B", "C", "D", "E", "F", "G", "a", "b", "c", "d", "e", "f", "g", "aa", "bb", "cc", "dd", "ee"]
            for note in ["Gamma", "e", "ee"]:
                print(note, "is at position", gamut.index(note))
            print("notes in the gamut:", len(gamut))
            ### gamut.c
            #include <stdio.h>
            #include <string.h>

            int main(void) {
                const char *gamut[] = {"Gamma", "A", "B", "C", "D", "E", "F", "G", "a", "b", "c", "d", "e", "f", "g", "aa", "bb", "cc", "dd", "ee"};
                const char *wanted[] = {"Gamma", "e", "ee"};
                int count = sizeof gamut / sizeof gamut[0];
                for (int w = 0; w < 3; w++) {
                    for (int p = 0; p < count; p++) {
                        if (strcmp(gamut[p], wanted[w]) == 0) {
                            printf("%s is at position %d\n", wanted[w], p);
                        }
                    }
                }
                printf("notes in the gamut: %d\n", count);
                return 0;
            }
            ### run.sh
            python3 gamut.py > python.txt
            cc -o gamut_c gamut.c
            ./gamut_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
