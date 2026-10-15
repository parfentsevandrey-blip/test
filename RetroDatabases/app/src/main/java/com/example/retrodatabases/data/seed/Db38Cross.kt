package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiments of 1660 to 1670: Cassini's rotation of Jupiter and Sydenham's fever days, each in Python and C. */
val db38Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 38,
        kind = Kind.CROSS,
        section = "Astronomy",
        title = "Cassini's Jupiter rotation in Python and C",
        originalTitle = "Spots on Jupiter",
        author = "Giovanni Domenico Cassini",
        year = 1665,
        summary = "Cassini's rotation period for Jupiter is turned into minutes and degrees per hour once in Python and once in C, and a script checks that both print the same lines.",
        details = "Cassini watched spots on Jupiter in 1665 and gave the planet a rotation of about nine hours and fifty-six minutes. Both programs use that figure, and the run script compares the printed outputs line by line.",
        instructions = """
            1. Save the example as cassini_jupiter.py, cassini_jupiter.c and run.sh.
            2. Run it: sh run.sh
        """,
        example = """
            ### cassini_jupiter.py
            print("period in minutes:", 9 * 60 + 56)
            print("degrees per hour:", round(360 / ((9 * 60 + 56) / 60), 2))
            ### cassini_jupiter.c
            #include <stdio.h>

            int main(void) {
                int minutes = 9 * 60 + 56;
                printf("period in minutes: %d\n", minutes);
                printf("degrees per hour: %.2f\n", 360.0 / (minutes / 60.0));
                return 0;
            }
            ### run.sh
            python3 cassini_jupiter.py > python.txt
            cc -o cassini_jupiter_c cassini_jupiter.c
            ./cassini_jupiter_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

    entry(
        databaseId = 38,
        kind = Kind.CROSS,
        section = "Medicine",
        title = "Sydenham's fever days in Python and C",
        originalTitle = "Methodus curandi febres",
        author = "Thomas Sydenham",
        year = 1666,
        summary = "The attack days of a tertian and a quartan fever are listed once in Python and once in C, and a script checks that both programs print the same days.",
        details = "Sydenham's book of 1666 on the cure of fevers described the intermittent kinds, and the tertian and quartan names mark intervals of 48 and 72 hours. Both programs print the attack days for ten days, and the run script compares the printed lines.",
        instructions = """
            1. Save the example as sydenham_fevers.py, sydenham_fevers.c and run.sh.
            2. Run it: sh run.sh
        """,
        example = """
            ### sydenham_fevers.py
            INTERVALS = {"tertian": 2, "quartan": 3}   # days between attacks: 48 hours and 72 hours

            def attack_days(interval, last_day):
                # The first attack falls on day 1, and each later one comes one interval after the last.
                return list(range(1, last_day + 1, interval))

            for kind in ("tertian", "quartan"):
                days = attack_days(INTERVALS[kind], 10)
                print(f"{kind}, every {INTERVALS[kind] * 24} hours: attacks on days {' '.join(str(d) for d in days)}")
            ### sydenham_fevers.c
            #include <stdio.h>

            /* Intermittent fevers return every second day (tertian) or every third day (quartan); the first attack is on day 1. */
            static void print_attacks(const char *kind, int interval, int last_day) {
                printf("%s, every %d hours: attacks on days", kind, interval * 24);
                for (int day = 1; day <= last_day; day += interval) {
                    printf(" %d", day);
                }
                printf("\n");
            }

            int main(void) {
                print_attacks("tertian", 2, 10);
                print_attacks("quartan", 3, 10);
                return 0;
            }
            ### run.sh
            python3 sydenham_fevers.py > python.txt
            cc -o sydenham_fevers_c sydenham_fevers.c
            ./sydenham_fevers_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
