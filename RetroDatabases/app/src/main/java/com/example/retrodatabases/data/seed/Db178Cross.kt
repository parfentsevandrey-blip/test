package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiment of 262: the line counts of the three versions of Shapur I's inscription, totalled in Python and in C. */
val db178Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 178,
        kind = Kind.CROSS,
        section = "Inscriptions",
        title = "The lines of Shapur I's three versions, totalled and compared in Python and C",
        originalTitle = "Res Gestae Divi Saporis",
        author = "Shapur I's court",
        year = 262,
        summary = "The Greek, Middle Persian and Parthian versions of Shapur I's inscription run to 70, 35 and 30 lines, and a Python and a C program total them.",
        details = "The three versions total 135 lines, and the Greek version is about twice the length of the Middle Persian one, so the program prints that ratio. The run script compares the output of the two programs line by line, and 262 is the usual date of the inscription.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### lines.py
            LINES = {"Greek": 70, "Middle Persian": 35, "Parthian": 30}   # line counts of the three versions
            for version, lines in LINES.items():
                print(version, lines, "lines")
            print("total lines:", sum(LINES.values()))
            print("Greek over Middle Persian:", round(LINES["Greek"] / LINES["Middle Persian"], 1))
            ### lines.c
            #include <stdio.h>

            int main(void) {
                /* line counts of the three versions, as given for each version */
                int greek = 70, middle_persian = 35, parthian = 30;
                printf("Greek %d lines\n", greek);
                printf("Middle Persian %d lines\n", middle_persian);
                printf("Parthian %d lines\n", parthian);
                printf("total lines: %d\n", greek + middle_persian + parthian);
                printf("Greek over Middle Persian: %.1f\n", (double)greek / middle_persian);
                return 0;
            }
            ### run.sh
            python3 lines.py > python.txt
            cc -o lines_c lines.c
            ./lines_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
        rare = true,
    ),

)
