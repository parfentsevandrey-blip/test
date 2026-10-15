package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiments of 1740 to 1750: d'Alembert's least modulus and a maximum found by fluxions, each in Python and C. */
val db30Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 30,
        kind = Kind.CROSS,
        section = "Algebra",
        title = "D'Alembert's attempt at the fundamental theorem of algebra",
        originalTitle = "Recherches sur le calcul integral",
        author = "Jean le Rond d'Alembert",
        year = 1746,
        summary = "A polynomial's modulus is searched for its least value, which should be zero at a root, and the program runs that search for z squared plus 1 in Python and in C.",
        details = "D'Alembert's argument of 1746 for the fundamental theorem of algebra looked for the least modulus of a polynomial, and its gap was the existence of that least value, which later proofs closed. The example searches the integer grid from -2 to 2 for the smallest modulus of z squared plus 1, which is zero at z = i and z = -i, and runs the same search in C.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### dalembert_min.py
            def modulus_squared(x, y):
                # z = x + y i gives p(z) = z^2 + 1 = (x^2 - y^2 + 1) + 2xy i, whose squared modulus is an integer.
                re = x * x - y * y + 1
                im = 2 * x * y
                return re * re + im * im

            grid = [(x, y) for x in range(-2, 3) for y in range(-2, 3)]
            smallest = min(modulus_squared(x, y) for x, y in grid)
            print("smallest |p|^2 on the grid:", smallest)
            print("grid points where it is attained:", [(x, y) for x, y in grid if modulus_squared(x, y) == smallest])
            print("|p|^2 at z = 0:", modulus_squared(0, 0))
            ### dalembert_min.c
            #include <stdio.h>

            static long modulus_squared(int x, int y) {
                /* z = x + y i gives p(z) = (x^2 - y^2 + 1) + 2xy i. */
                long re = (long)x * x - (long)y * y + 1;
                long im = 2L * x * y;
                return re * re + im * im;
            }

            int main(void) {
                long smallest = -1;
                for (int x = -2; x <= 2; x++) {
                    for (int y = -2; y <= 2; y++) {
                        long v = modulus_squared(x, y);
                        if (smallest < 0 || v < smallest) {
                            smallest = v;
                        }
                    }
                }
                printf("smallest |p|^2 on the grid: %ld\n", smallest);
                printf("grid points where it is attained: [");
                int first = 1;
                for (int x = -2; x <= 2; x++) {
                    for (int y = -2; y <= 2; y++) {
                        if (modulus_squared(x, y) == smallest) {
                            printf("%s(%d, %d)", first ? "" : ", ", x, y);
                            first = 0;
                        }
                    }
                }
                printf("]\n");
                printf("|p|^2 at z = 0: %ld\n", modulus_squared(0, 0));
                return 0;
            }
            ### run.sh
            python3 dalembert_min.py > python.txt
            cc -o dalembert_min_c dalembert_min.c
            ./dalembert_min_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

    entry(
        databaseId = 30,
        kind = Kind.CROSS,
        section = "Maxima",
        title = "Maclaurin's fluxion for a maximum",
        originalTitle = "A Treatise of Fluxions",
        author = "Colin Maclaurin",
        year = 1742,
        summary = "A maximum of a curve is where its fluxion vanishes, and the program finds the largest whole-number value of x (10 - x) in Python and in C.",
        details = "Maclaurin's Treatise of 1742 uses the fluxion, the rate of change, to find maxima and minima by setting it to zero. The example takes y = x (10 - x), whose fluxion is 10 - 2x, finds the largest whole-number value at x = 5, where y = 25, and runs the same search in C to compare.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### maxima.py
            def y(x):
                return x * (10 - x)

            def fluxion(x):
                # Maclaurin's fluxion of y = x (10 - x) is 10 - 2x, which vanishes at x = 5.
                return 10 - 2 * x

            best = max(range(0, 11), key=y)
            print(f"fluxion at x = 5: {fluxion(5)}")
            print(f"largest value of y: x = {best}, y = {y(best)}")
            print(f"fluxion at the best x: {fluxion(best)}")
            ### maxima.c
            #include <stdio.h>

            static int y(int x) { return x * (10 - x); }
            static int fluxion(int x) { return 10 - 2 * x; }

            int main(void) {
                int best = 0;
                for (int x = 0; x <= 10; x++) {
                    if (y(x) > y(best)) {
                        best = x;
                    }
                }
                printf("fluxion at x = 5: %d\n", fluxion(5));
                printf("largest value of y: x = %d, y = %d\n", best, y(best));
                printf("fluxion at the best x: %d\n", fluxion(best));
                return 0;
            }
            ### run.sh
            python3 maxima.py > python.txt
            cc -o maxima_c maxima.c
            ./maxima_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),
)
