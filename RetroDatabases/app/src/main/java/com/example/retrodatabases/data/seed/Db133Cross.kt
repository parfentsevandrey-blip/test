package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code piece of 710 to 720: the years between Liutprand's accession, the Fudoki decree and the Codex Amiatinus, computed in Python and in C. */
val db133Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 133,
        kind = Kind.CROSS,
        section = "Years",
        title = "The years between Liutprand's accession in 712, the Fudoki decree of 713 and the Codex Amiatinus of 716, in Python and C",
        originalTitle = "Years between three events",
        author = "Liutprand, king of the Lombards",
        year = 712,
        summary = "The years between Liutprand's accession, the Fudoki decree and the Codex Amiatinus are printed by a Python program and by a C program, and a script checks that both print the same figures.",
        details = "The dates used are 712 for Liutprand's accession, 713 for the Fudoki decree and 716 for the Codex Amiatinus, and the three spans printed are 1, 3 and 4 years. The dates are the usual ones for these events, and the figures show only the arithmetic of the dates, not any link between a Lombard king, a Japanese court and a Northumbrian monastery.",
        instructions = """
            1. Save the example as spans.py, spans.c and run.sh in one folder.
            2. Run it: sh run.sh
        """,
        example = """
            ### spans.py
            ACCESSION = 712
            FUDOKI = 713
            CODEX = 716
            print("years from Liutprand's accession to the Fudoki decree:", FUDOKI - ACCESSION)
            print("years from the Fudoki decree to the Codex Amiatinus:", CODEX - FUDOKI)
            print("years from Liutprand's accession to the Codex Amiatinus:", CODEX - ACCESSION)
            ### spans.c
            #include <stdio.h>

            int main(void) {
                int accession = 712, fudoki = 713, codex = 716;
                printf("years from Liutprand's accession to the Fudoki decree: %d\n", fudoki - accession);
                printf("years from the Fudoki decree to the Codex Amiatinus: %d\n", codex - fudoki);
                printf("years from Liutprand's accession to the Codex Amiatinus: %d\n", codex - accession);
                return 0;
            }
            ### run.sh
            python3 spans.py > python.txt
            cc -o spans_c spans.c
            ./spans_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
