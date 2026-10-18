package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiment of 940: the dotted and undotted letters of the Arabic alphabet, counted in Python and in C. */
val db111Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 111,
        kind = Kind.CROSS,
        section = "Writing",
        title = "Dotted and undotted letters of the Arabic alphabet counted in Python and C",
        originalTitle = "Dotted and undotted letters of the Arabic alphabet counted in Python and C",
        author = "Arabic scribes",
        year = 940,
        summary = "A Python program and a C program count the 28 letters of the Arabic alphabet by their dots, with 13 bare letters, 10 with one dot, 3 with two and 2 with three, and a script checks that both give the same result.",
        details = "The dots that tell the Arabic letters apart are the marks that scribes added to the consonant shapes, and the counts follow the usual classification of the 28 letters. The year 940 is a period anchor, the year of the Cordoba anthology of this decade, since the dotted letters are older than this decade and the sources do not date their dotting to 940.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### dots.py
            letters = [
                ("alif", 0), ("ba", 1), ("ta", 2), ("tha", 3), ("jim", 1), ("ha", 0), ("kha", 1),
                ("dal", 0), ("dhal", 1), ("ra", 0), ("zay", 1), ("sin", 0), ("shin", 3), ("sad", 0),
                ("dad", 1), ("ta emphatic", 0), ("za", 1), ("ayn", 0), ("ghayn", 1), ("fa", 1),
                ("qaf", 2), ("kaf", 0), ("lam", 0), ("mim", 0), ("nun", 1), ("ha final", 0),
                ("waw", 0), ("ya", 2),
            ]
            print("letters:", len(letters))
            print("no dots:", len([n for n, d in letters if d == 0]))
            print("one dot:", len([n for n, d in letters if d == 1]))
            print("two dots:", len([n for n, d in letters if d == 2]))
            print("three dots:", len([n for n, d in letters if d == 3]))
            print("dotted letters:", len([n for n, d in letters if d > 0]))
            print("most dots:", max(d for _, d in letters))
            ### dots.c
            #include <stdio.h>

            struct letter { const char *name; int dots; };

            static const struct letter letters[] = {
                {"alif", 0}, {"ba", 1}, {"ta", 2}, {"tha", 3}, {"jim", 1}, {"ha", 0}, {"kha", 1},
                {"dal", 0}, {"dhal", 1}, {"ra", 0}, {"zay", 1}, {"sin", 0}, {"shin", 3}, {"sad", 0},
                {"dad", 1}, {"ta emphatic", 0}, {"za", 1}, {"ayn", 0}, {"ghayn", 1}, {"fa", 1},
                {"qaf", 2}, {"kaf", 0}, {"lam", 0}, {"mim", 0}, {"nun", 1}, {"ha final", 0},
                {"waw", 0}, {"ya", 2},
            };

            int main(void) {
                int n = (int)(sizeof letters / sizeof letters[0]);
                int count[4] = {0, 0, 0, 0};
                int most = 0;
                for (int i = 0; i < n; i++) {
                    count[letters[i].dots]++;
                    if (letters[i].dots > most) most = letters[i].dots;
                }
                printf("letters: %d\n", n);
                printf("no dots: %d\n", count[0]);
                printf("one dot: %d\n", count[1]);
                printf("two dots: %d\n", count[2]);
                printf("three dots: %d\n", count[3]);
                printf("dotted letters: %d\n", n - count[0]);
                printf("most dots: %d\n", most);
                return 0;
            }
            ### run.sh
            python3 dots.py > python.txt
            cc -o dots_c dots.c
            ./dots_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
