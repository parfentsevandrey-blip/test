package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiments of 1570 to 1580: the parallax of the Moon and of a star, and Greek letter numbers, each in Python and C. */
val db47Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 47,
        kind = Kind.CROSS,
        section = "Astronomy",
        title = "Parallax of the Moon and of a distant star in Python and C",
        originalTitle = "De nova stella",
        author = "Tycho Brahe",
        year = 1573,
        summary = "Parallax is the shift of a body against the distant stars when it is seen from two places, and a Python program and a C program print the same shifts for the Moon and for a star.",
        details = "The shift in arcseconds is about 206265 divided by the distance in Earth radii, for a baseline of one Earth radius, and Tycho's test of the new star used the same kind of reasoning. The run script compares the two outputs line by line and prints the same result when they agree.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### parallax.py
            RADIAN_IN_ARCSECONDS = 206265   # about 206265 arcseconds in one radian, rounded

            for name, distance in (("Moon", 60), ("star", 1000)):
                # The baseline is one Earth radius, and the distance is given in Earth radii.
                print(f"{name} at {distance} radii: {RADIAN_IN_ARCSECONDS // distance} arcseconds")
            ### parallax.c
            #include <stdio.h>

            int main(void) {
                const char *names[] = {"Moon", "star"};
                int distances[] = {60, 1000};
                for (int i = 0; i < 2; i++) {
                    printf("%s at %d radii: %d arcseconds\n", names[i], distances[i], 206265 / distances[i]);
                }
                return 0;
            }
            ### run.sh
            python3 parallax.py > python.txt
            cc -o parallax_c parallax.c
            ./parallax_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

    entry(
        databaseId = 47,
        kind = Kind.CROSS,
        section = "Numerals",
        title = "Greek letter numbers in Python and C",
        originalTitle = "Thesaurus Graecae linguae",
        author = "Henri Estienne",
        year = 1572,
        summary = "The greedy spelling of Greek letter numbers gives the same names in a Python program and a C program, and a script checks that the two agree for three numbers.",
        details = "Each number is split into the largest letter values first, using the Greek letters for the units, the tens and the hundreds. The C program uses a table of values and names, and the run script compares its output with the Python output line by line.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### greek.py
            GREEK = [(900, "sampi"), (800, "omega"), (700, "psi"), (600, "chi"), (500, "phi"),
                     (400, "upsilon"), (300, "tau"), (200, "sigma"), (100, "rho"), (90, "koppa"),
                     (80, "pi"), (70, "omicron"), (60, "xi"), (50, "nu"), (40, "mu"),
                     (30, "lambda"), (20, "kappa"), (10, "iota"), (9, "theta"), (8, "eta"),
                     (7, "zeta"), (5, "epsilon"), (4, "delta"), (3, "gamma"), (2, "beta"), (1, "alpha")]

            def spell(number):
                names = []
                for value, name in GREEK:
                    while number >= value:
                        names.append(name)
                        number -= value
                return " ".join(names)

            for number in (318, 64, 905):
                print(f"{number}: {spell(number)}")
            ### greek.c
            #include <stdio.h>

            struct letter {
                int value;
                const char *name;
            };

            static const struct letter GREEK[] = {
                {900, "sampi"}, {800, "omega"}, {700, "psi"}, {600, "chi"}, {500, "phi"},
                {400, "upsilon"}, {300, "tau"}, {200, "sigma"}, {100, "rho"}, {90, "koppa"},
                {80, "pi"}, {70, "omicron"}, {60, "xi"}, {50, "nu"}, {40, "mu"},
                {30, "lambda"}, {20, "kappa"}, {10, "iota"}, {9, "theta"}, {8, "eta"},
                {7, "zeta"}, {5, "epsilon"}, {4, "delta"}, {3, "gamma"}, {2, "beta"}, {1, "alpha"}
            };

            int main(void) {
                int numbers[] = {318, 64, 905};
                int count = (int)(sizeof GREEK / sizeof GREEK[0]);
                for (int i = 0; i < 3; i++) {
                    int number = numbers[i];
                    printf("%d:", numbers[i]);
                    for (int k = 0; k < count; k++) {
                        while (number >= GREEK[k].value) {
                            printf(" %s", GREEK[k].name);
                            number -= GREEK[k].value;
                        }
                    }
                    printf("\n");
                }
                return 0;
            }
            ### run.sh
            python3 greek.py > python.txt
            cc -o greek_c greek.c
            ./greek_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
