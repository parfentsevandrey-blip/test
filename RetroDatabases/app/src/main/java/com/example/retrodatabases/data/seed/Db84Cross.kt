package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiment of 1206: the strike times of al-Jazari's pegged drum, in Python and in C. */
val db84Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 84,
        kind = Kind.CROSS,
        section = "Automata",
        title = "The strike times of al-Jazari's pegged drum in Python and C",
        originalTitle = "Pegged drum",
        author = "Ibn al-Razzaz al-Jazari",
        year = 1206,
        summary = "Pegs on a turning drum set when each hammer strikes in al-Jazari's 1206 band, and a script checks that the Python and C programs print the same strike times.",
        details = "The example is illustrative: it sets four pegs at chosen angles on a drum that turns once every four seconds, and both programs print the time of each strike. The run script compares the two outputs line by line.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### pegs.py
            PEG_ANGLES = [30, 120, 210, 300]   # illustrative peg positions, in degrees
            TURN_SECONDS = 4.0                  # one full turn of the drum
            for peg, angle in enumerate(PEG_ANGLES, start=1):
                strike = angle / 360 * TURN_SECONDS
                print(f"peg {peg} strikes at {strike:.3f} s")
            ### pegs.c
            #include <stdio.h>

            int main(void) {
                const int peg_angles[] = {30, 120, 210, 300};   /* illustrative peg positions, in degrees */
                const double turn_seconds = 4.0;                /* one full turn of the drum */
                for (int peg = 0; peg < 4; peg++) {
                    double strike = peg_angles[peg] / 360.0 * turn_seconds;
                    printf("peg %d strikes at %.3f s\n", peg + 1, strike);
                }
                return 0;
            }
            ### run.sh
            python3 pegs.py > python.txt
            cc -o pegs_c pegs.c
            ./pegs_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
