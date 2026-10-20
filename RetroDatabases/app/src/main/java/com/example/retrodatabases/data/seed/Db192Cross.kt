package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiment of 122: the sods in one metre of an illustrative turf rampart, counted in Python and in C. */
val db192Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 192,
        kind = Kind.CROSS,
        section = "Earthworks",
        title = "Turves in one metre of a turf rampart, counted in Python and C",
        originalTitle = "Turf rampart",
        author = "Roman soldiers of the frontier",
        year = 122,
        summary = "The first build of Hadrian's Wall used turf in part, and a short Python program and a short C program count the sods in one metre of an illustrative rampart.",
        details = "The sizes are illustrative, a rampart 3 metres wide and 2 metres high with sods 30 by 20 by 10 centimetres, so one metre of rampart holds 1000 sods. The run script compares the two outputs line by line, and the count checks arithmetic, not any figure from the wall.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### turves.py
            WIDTH_CM = 300    # illustrative width of the rampart
            HEIGHT_CM = 200   # illustrative height
            LENGTH_CM = 100   # one metre of rampart
            SOD = 30 * 20 * 10   # illustrative sod, in cubic centimetres
            volume = WIDTH_CM * HEIGHT_CM * LENGTH_CM
            print("sods in one metre:", volume // SOD)
            print("rampart volume in cubic cm:", volume)
            ### turves.c
            #include <stdio.h>

            int main(void) {
                int width = 300, height = 200, length = 100;   /* illustrative rampart, in cm */
                int sod = 30 * 20 * 10;                        /* illustrative sod, in cubic cm */
                int volume = width * height * length;
                printf("sods in one metre: %d\n", volume / sod);
                printf("rampart volume in cubic cm: %d\n", volume);
                return 0;
            }
            ### run.sh
            python3 turves.py > python.txt
            cc -o turves_c turves.c
            ./turves_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
