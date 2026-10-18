package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiment of 1267: the letter counts of the Hebrew, Greek and Arabic alphabets that Roger Bacon wanted Latin scholars to study, in Python and in C. */
val db78Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 78,
        kind = Kind.CROSS,
        section = "Alphabets",
        title = "Letter counts of the Hebrew, Greek and Arabic alphabets in Python and C",
        originalTitle = "Three alphabets",
        author = "Roger Bacon",
        year = 1267,
        summary = "Bacon's Opus majus urged the study of Greek, Hebrew and Arabic, and a script checks that the Python and C programs print the same letter counts for those three alphabets.",
        details = "The counts are the standard ones, 22 for Hebrew, 24 for Greek and 28 for Arabic, and the two programs print them in the same order. The run script compares the two outputs line by line.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### alphabets.py
            ALPHABETS = {"Hebrew": 22, "Greek": 24, "Arabic": 28}
            for name, letters in ALPHABETS.items():
                print(name, letters)
            print("total letters:", sum(ALPHABETS.values()))
            ### alphabets.c
            #include <stdio.h>

            int main(void) {
                const char *names[] = {"Hebrew", "Greek", "Arabic"};
                int letters[] = {22, 24, 28};
                int total = 0;
                for (int i = 0; i < 3; i++) {
                    printf("%s %d\n", names[i], letters[i]);
                    total += letters[i];
                }
                printf("total letters: %d\n", total);
                return 0;
            }
            ### run.sh
            python3 alphabets.py > python.txt
            cc -o alphabets_c alphabets.c
            ./alphabets_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
