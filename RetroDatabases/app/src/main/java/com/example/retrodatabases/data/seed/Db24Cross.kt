package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiments of 1800 to 1810: the same result from two languages, checked by a run script. */
val db24Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 24,
        kind = Kind.CROSS,
        section = "Polarized light",
        title = "Malus's law for polarized light, in Python and C",
        originalTitle = "Polarization of light by reflection, Malus (1809)",
        author = "Etienne-Louis Malus",
        year = 1809,
        summary = "Malus's law gives the light passing a second polarizer as cos squared of the angle, and Python and C each print the fraction for five angles.",
        details = "Malus published the law in 1809, after his 1808 observations of light polarized by reflection. The two programs print the fraction of light passing at angles of 0, 30, 45, 60 and 90 degrees, and the run script compares their output.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### malus.py
            import math

            # Malus's law: light of intensity I0 passing a second polarizer at angle theta keeps I0 times cos squared theta.
            for angle in (0, 30, 45, 60, 90):
                factor = math.cos(math.radians(angle)) ** 2
                print(f"angle {angle} degrees: fraction {factor:.4f}")
            ### malus.c
            #include <math.h>
            #include <stdio.h>

            int main(void) {
                const double pi = 3.14159265358979323846;
                int angles[5] = {0, 30, 45, 60, 90};
                for (int i = 0; i < 5; i++) {
                    double c = cos(angles[i] * pi / 180.0);
                    printf("angle %d degrees: fraction %.4f\n", angles[i], c * c);
                }
                return 0;
            }
            ### run.sh
            python3 malus.py > python.txt
            cc -o malus_c malus.c -lm
            ./malus_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

    entry(
        databaseId = 24,
        kind = Kind.CROSS,
        section = "Sums of squares",
        title = "Sums of two squares up to 30, in Python and C",
        originalTitle = "Disquisitiones Arithmeticae, binary forms and sums of squares (1801)",
        author = "Carl Friedrich Gauss",
        year = 1801,
        summary = "The numbers up to 30 that are sums of two squares are found by search in Python and in C, and a run script checks that the two lists agree.",
        details = "Fermat and Euler studied which numbers are sums of two squares, and Gauss's binary forms of 1801 give the same question a systematic treatment. The two programs search the squares up to 25 and print the fifteen numbers they find, and the run script compares their output.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### two_squares.py
            # Which n up to 30 are a^2 + b^2 with a and b at least 0?
            found = [n for n in range(1, 31) if any(a * a + b * b == n for a in range(0, 6) for b in range(0, 6))]
            print("sums of two squares up to 30:", " ".join(str(n) for n in found))
            print("count:", len(found))
            ### two_squares.c
            #include <stdio.h>

            int main(void) {
                int count = 0;
                printf("sums of two squares up to 30:");
                for (int n = 1; n <= 30; n++) {
                    int found = 0;
                    for (int a = 0; a < 6 && !found; a++) {
                        for (int b = 0; b < 6 && !found; b++) {
                            if (a * a + b * b == n) {
                                found = 1;
                            }
                        }
                    }
                    if (found) {
                        printf(" %d", n);
                        count++;
                    }
                }
                printf("\n");
                printf("count: %d\n", count);
                return 0;
            }
            ### run.sh
            python3 two_squares.py > python.txt
            cc -o two_squares_c two_squares.c
            ./two_squares_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),
)
