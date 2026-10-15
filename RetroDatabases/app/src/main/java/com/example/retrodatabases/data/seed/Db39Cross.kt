package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiments of 1650 to 1660: Huygens's centrifugal force and a Cape ration ledger, each in Python and C. */
val db39Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 39,
        kind = Kind.CROSS,
        section = "Mechanics",
        title = "Huygens's centrifugal force in Python and C",
        originalTitle = "De vi centrifuga",
        author = "Christiaan Huygens",
        year = 1659,
        summary = "Huygens's manuscript De vi centrifuga of 1659 took up the outward pull of a body moving round a circle, which is the body's mass times the square of its speed, divided by the radius.",
        details = "The manuscript De vi centrifuga dates from 1659, and the rule for a body moving round a circle at uniform speed was printed in Huygens's Horologium oscillatorium of 1673. The two programs apply the rule to three whole-number cases, and the script checks that both print the same pulls.",
        instructions = """
            1. Save the first program as centrifugal.py, the second as centrifugal.c, and the script as run.sh in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### centrifugal.py
            from fractions import Fraction

            def centrifugal_force(mass, speed, radius):
                # Huygens's rule: the outward pull is the mass times the square of the speed, divided by the radius.
                return Fraction(mass * speed * speed, radius)

            for mass, speed, radius in ((2, 3, 1), (1, 6, 4), (3, 10, 5)):
                print(f"mass {mass}, speed {speed}, radius {radius}: pull {centrifugal_force(mass, speed, radius)}")
            ### centrifugal.c
            #include <stdio.h>

            static int centrifugal_force(int mass, int speed, int radius) {
                /* Huygens's rule: the outward pull is the mass times the square of the speed, divided by the radius. */
                return mass * speed * speed / radius;
            }

            int main(void) {
                printf("mass %d, speed %d, radius %d: pull %d\n", 2, 3, 1, centrifugal_force(2, 3, 1));
                printf("mass %d, speed %d, radius %d: pull %d\n", 1, 6, 4, centrifugal_force(1, 6, 4));
                printf("mass %d, speed %d, radius %d: pull %d\n", 3, 10, 5, centrifugal_force(3, 10, 5));
                return 0;
            }
            ### run.sh
            python3 centrifugal.py > python.txt
            cc -o centrifugal_c centrifugal.c
            ./centrifugal_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

    entry(
        databaseId = 39,
        kind = Kind.CROSS,
        section = "Provisioning",
        title = "Cape station ration ledger in Python and C",
        originalTitle = "Cape station ration ledger in Python and C",
        author = "Jan van Riebeeck",
        year = 1652,
        summary = "A ration ledger for two ship's crews at the Cape station is computed once in Python and once in C, and a script checks that both print the same totals.",
        details = "The Dutch station at Table Bay was set up in 1652 to supply passing ships with water and food. The rations in the example are illustrative figures, and the two programs use whole-number arithmetic so that their totals match exactly.",
        instructions = """
            1. Save the first program as cape_ledger.py, the second as cape_ledger.c, and the script as run.sh in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### cape_ledger.py
            CREWS = [(100, 30), (200, 10)]   # (men, days)
            WATER_PER_MAN = 4                # litres per man per day, illustrative
            BISCUIT_PER_MAN = 500            # grams per man per day, illustrative
            for men, days in CREWS:
                water = men * days * WATER_PER_MAN
                biscuit_kg = men * days * BISCUIT_PER_MAN // 1000
                print(f"crew {men}, days {days}: water {water} litres, biscuit {biscuit_kg} kg")
            ### cape_ledger.c
            #include <stdio.h>

            int main(void) {
                int crews[2][2] = {{100, 30}, {200, 10}};
                int water_per_man = 4;
                int biscuit_per_man = 500;
                for (int i = 0; i < 2; i++) {
                    int men = crews[i][0];
                    int days = crews[i][1];
                    int water = men * days * water_per_man;
                    int biscuit_kg = men * days * biscuit_per_man / 1000;
                    printf("crew %d, days %d: water %d litres, biscuit %d kg\n", men, days, water, biscuit_kg);
                }
                return 0;
            }
            ### run.sh
            python3 cape_ledger.py > python.txt
            cc -o cape_ledger_c cape_ledger.c
            ./cape_ledger_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
