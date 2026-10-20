package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One cross-code piece of 640 to 650: Taizong's lifespan from his birth in 598 to his death in 649, computed in Python and in C. */
val db140Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 140,
        kind = Kind.CROSS,
        section = "Chinese",
        title = "The lifespan of Taizong, 598 to 649, computed in Python and C",
        originalTitle = "Taizong",
        author = "Li Shimin, Emperor Taizong of Tang",
        year = 649,
        summary = "Taizong, the second Tang emperor, lived from 598 to 649, and a Python program and a C program both print his age at death as 51.",
        details = "Taizong was born in 598 and died in 649, so by the simple count of years he was 51 at his death. The two programs print the same figure, which checks the subtraction only.",
        rare = true,
        instructions = """
            1. Save the example as taizong_age.py, taizong_age.c and run.sh in one folder.
            2. Run it: sh run.sh
        """,
        example = """
            ### taizong_age.py
            BIRTH = 598  # Taizong's birth year, as usually given
            DEATH = 649  # Taizong's death year, as usually given
            print("age at death:", DEATH - BIRTH)
            ### taizong_age.c
            #include <stdio.h>

            int main(void) {
                int birth = 598, death = 649;
                printf("age at death: %d\n", death - birth);
                return 0;
            }
            ### run.sh
            python3 taizong_age.py > python.txt
            cc -o taizong_age_c taizong_age.c
            ./taizong_age_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
