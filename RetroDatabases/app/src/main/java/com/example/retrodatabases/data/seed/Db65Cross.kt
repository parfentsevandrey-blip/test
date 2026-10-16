package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiments of 1390 to 1400: the ogham letters grouped in aicmi and the Julian leap years of the decade, each in Python and C. */
val db65Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 65,
        kind = Kind.CROSS,
        section = "Letters",
        title = "Ogham letters by aicme in Python and C",
        originalTitle = "Ogham aicme",
        author = "Irish grammarians of the medieval tradition",
        year = 1390,
        summary = "The twenty ogham letters fall into four groups of five called aicmi, and a script checks that the Python and C programs print the same groups and the same counts.",
        details = "The grouping and the five extra letters, called forfeda, are the ones named in the Irish grammatical tradition that the Book of Ballymote copies, and the example prints the letters of each group. The run script compares the two outputs line by line.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### ogham.py
            AICME = [
                ["b", "l", "f", "s", "n"],
                ["h", "d", "t", "c", "q"],
                ["m", "g", "ng", "st", "r"],
                ["a", "o", "u", "e", "i"],
            ]
            FORFEDA = ["ea", "oi", "ui", "io", "ae"]
            total = 0
            for number, group in enumerate(AICME, start=1):
                print("aicme", number, len(group), "".join(group))
                total += len(group)
            print("letters in the four aicmi:", total)
            print("forfeda:", len(FORFEDA))
            ### ogham.c
            #include <stdio.h>

            int main(void) {
                const char *aicme[4][5] = {
                    {"b", "l", "f", "s", "n"},
                    {"h", "d", "t", "c", "q"},
                    {"m", "g", "ng", "st", "r"},
                    {"a", "o", "u", "e", "i"},
                };
                int total = 0;
                for (int row = 0; row < 4; row++) {
                    int count = 5;
                    printf("aicme %d %d ", row + 1, count);
                    for (int col = 0; col < 5; col++) {
                        printf("%s", aicme[row][col]);
                    }
                    printf("\n");
                    total += count;
                }
                printf("letters in the four aicmi: %d\n", total);
                printf("forfeda: %d\n", 5);
                return 0;
            }
            ### run.sh
            python3 ogham.py > python.txt
            cc -o ogham_c ogham.c
            ./ogham_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

    entry(
        databaseId = 65,
        kind = Kind.CROSS,
        section = "Calendar",
        title = "Julian leap years from 1390 to 1400 in Python and C",
        originalTitle = "Julian leap years",
        author = "The Julian calendar",
        year = 1390,
        summary = "Under the Julian rule the years 1390 to 1400 hold three leap years, 1392, 1396 and 1400, and a script checks that the Python and C programs list them alike.",
        details = "The rule counts every year divisible by four, and the programs print the count and the years in the same order. The run script compares the two outputs line by line.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### leap.py
            leaps = [year for year in range(1390, 1401) if year % 4 == 0]
            print("leap years:", len(leaps))
            for year in leaps:
                print(year)
            ### leap.c
            #include <stdio.h>

            int main(void) {
                int count = 0;
                for (int year = 1390; year <= 1400; year++) {
                    if (year % 4 == 0) {
                        count++;
                    }
                }
                printf("leap years: %d\n", count);
                for (int year = 1390; year <= 1400; year++) {
                    if (year % 4 == 0) {
                        printf("%d\n", year);
                    }
                }
                return 0;
            }
            ### run.sh
            python3 leap.py > python.txt
            cc -o leap_c leap.c
            ./leap_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
