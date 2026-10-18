package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiment of 926: the years from the traditional founding of Balhae to its fall, counted in Python and in C. */
val db112Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 112,
        kind = Kind.CROSS,
        section = "Balhae",
        title = "The span of Balhae from its founding to its fall, counted in Python and C",
        originalTitle = "The span of Balhae from its founding to its fall, counted in Python and C",
        author = "Dae Jo-yeong and the Khitan Liao state",
        year = 926,
        summary = "Python and C programs count the years from the traditional founding of Balhae in 698 to its fall in 926, and a script checks that both give 228.",
        details = "The founding date is traditionally 698, while counted from the Tang princely title of 713 the span would be 213 years, so the result depends on the start year chosen. The C program counts with plain integer subtraction and no date library, as does the Python program, so the run tests the arithmetic rather than the history.",
        rare = true,
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### balhae.py
            founded = 698       # traditional founding of Balhae
            fell = 926          # Khitan takeover, as commonly stated
            print("years from the founding of Balhae to its fall:", fell - founded)
            ### balhae.c
            #include <stdio.h>

            int main(void) {
                int founded = 698;   /* traditional founding of Balhae */
                int fell = 926;      /* Khitan takeover, as commonly stated */
                printf("years from the founding of Balhae to its fall: %d\n", fell - founded);
                return 0;
            }
            ### run.sh
            python3 balhae.py > python.txt
            cc -o balhae_c balhae.c
            ./balhae_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
