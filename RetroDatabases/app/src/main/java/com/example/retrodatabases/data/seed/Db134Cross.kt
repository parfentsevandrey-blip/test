package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One cross-code piece of 700 to 710: Liu Zhiji's age when the Shitong was completed and at his death, counted in Python and in C. */
val db134Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 134,
        kind = Kind.CROSS,
        section = "Chinese",
        title = "Liu Zhiji's age when the Shitong was completed in 710 and at his death in 721, counted in Python and C",
        originalTitle = "Shitong",
        author = "Liu Zhiji",
        year = 710,
        summary = "Liu Zhiji's age at the completion of the Shitong and at his death is counted by a Python program and by a C program, and a script checks that both print the same figures.",
        details = "Liu Zhiji's lifespan is usually given as 661 to 721, so he was 49 when the Shitong was completed in 710 and 60 at his death. The two programs print the same three figures, so the check confirms the arithmetic, while the dates come from the sources on his life.",
        rare = true,
        instructions = """
            1. Save the example as liu_ages.py, liu_ages.c and run.sh in one folder.
            2. Run it: sh run.sh
        """,
        example = """
            ### liu_ages.py
            BIRTH = 661    # the start of Liu Zhiji's lifespan, as usually given
            SHITONG = 710  # the Shitong completed in 710
            DEATH = 721    # the end of his lifespan, as usually given
            print("age at the completion of the Shitong:", SHITONG - BIRTH)
            print("age at his death:", DEATH - BIRTH)
            print("years between the Shitong and his death:", DEATH - SHITONG)
            ### liu_ages.c
            #include <stdio.h>

            int main(void) {
                int birth = 661, shitong = 710, death = 721;
                printf("age at the completion of the Shitong: %d\n", shitong - birth);
                printf("age at his death: %d\n", death - birth);
                printf("years between the Shitong and his death: %d\n", death - shitong);
                return 0;
            }
            ### run.sh
            python3 liu_ages.py > python.txt
            cc -o liu_ages_c liu_ages.c
            ./liu_ages_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
