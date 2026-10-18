package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiment of 987: the ten sections of Ibn al-Nadim's Fihrist, counted in Python and in C. */
val db106Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 106,
        kind = Kind.CROSS,
        section = "Baghdad",
        title = "The ten sections of the Fihrist, six Islamic and four other, counted in Python and C",
        originalTitle = "The ten sections of the Fihrist, six Islamic and four other, counted in Python and C",
        author = "Ibn al-Nadim",
        year = 987,
        summary = "Python and C programs count the ten sections of the Fihrist, six on Islamic writings and four on non-Islamic works, and a script checks that both print the same result.",
        details = "The C program uses an array and a loop, while the Python program uses a list of pairs, and both must print the six Islamic and four non-Islamic sections and a total of ten. The example tests a short fixed list of counts in two languages.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### sections.py
            sections = [("Islamic writings", 6), ("non-Islamic works and translations", 4)]
            total = 0
            for name, count in sections:
                print(name, count)
                total += count
            print("sections in all:", total)
            ### sections.c
            #include <stdio.h>

            int main(void) {
                const char *names[] = {"Islamic writings", "non-Islamic works and translations"};
                int counts[] = {6, 4};
                int total = 0;
                for (int i = 0; i < 2; i++) {
                    printf("%s %d\n", names[i], counts[i]);
                    total += counts[i];
                }
                printf("sections in all: %d\n", total);
                return 0;
            }
            ### run.sh
            python3 sections.py > python.txt
            cc -o sections_c sections.c
            ./sections_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
