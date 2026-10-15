package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiments of 1820 to 1830: the same result from two languages, checked by a run script. */
val db22Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 22,
        kind = Kind.CROSS,
        section = "Sums of squares",
        title = "Jacobi's four-square count in Python and C",
        originalTitle = "Fundamenta nova theoriae functionum ellipticarum (1829)",
        author = "Carl Gustav Jacob Jacobi",
        year = 1829,
        summary = "The number of ways to write n as a sum of four squares, with signs and order counted, is eight times the sum of the divisors of n that are not multiples of four, and two programs check it for n up to 10.",
        details = "Jacobi's four-square count, stated in his 1829 work on elliptic functions, is computed here by brute force and by the divisor formula. The run script checks that the Python and C programs print the same lines.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### jacobi4.py
            def r4_brute(n, bound):
                # Count integer 4-tuples (a, b, c, d) with a*a + b*b + c*c + d*d = n, signs and order counted.
                count = 0
                for a in range(-bound, bound + 1):
                    for b in range(-bound, bound + 1):
                        for c in range(-bound, bound + 1):
                            for d in range(-bound, bound + 1):
                                if a * a + b * b + c * c + d * d == n:
                                    count += 1
                return count

            def r4_formula(n):
                # Jacobi: eight times the sum of the divisors of n that are not multiples of four.
                return 8 * sum(d for d in range(1, n + 1) if n % d == 0 and d % 4 != 0)

            values = [r4_formula(n) for n in range(1, 11)]
            print(" ".join(str(v) for v in values))
            print("brute force agrees:", all(r4_brute(n, 3) == r4_formula(n) for n in range(1, 11)))
            ### jacobi4.c
            #include <stdio.h>

            /* Jacobi's formula: eight times the sum of the divisors of n that are not multiples of four. */
            static long r4_formula(long n) {
                long total = 0;
                for (long d = 1; d <= n; d++) {
                    if (n % d == 0 && d % 4 != 0) {
                        total += d;
                    }
                }
                return 8 * total;
            }

            /* Brute force: count integer 4-tuples whose squares sum to n, each entry between -3 and 3. */
            static long r4_brute(long n) {
                long count = 0;
                for (long a = -3; a <= 3; a++) {
                    for (long b = -3; b <= 3; b++) {
                        for (long c = -3; c <= 3; c++) {
                            for (long d = -3; d <= 3; d++) {
                                if (a * a + b * b + c * c + d * d == n) {
                                    count++;
                                }
                            }
                        }
                    }
                }
                return count;
            }

            int main(void) {
                for (long n = 1; n <= 10; n++) {
                    printf("%ld%s", r4_formula(n), n < 10 ? " " : "\n");
                }
                int agree = 1;
                for (long n = 1; n <= 10; n++) {
                    if (r4_brute(n) != r4_formula(n)) {
                        agree = 0;
                    }
                }
                printf("brute force agrees: %s\n", agree ? "True" : "False");
                return 0;
            }
            ### run.sh
            python3 jacobi4.py > python.txt
            cc -o jacobi4_c jacobi4.c
            ./jacobi4_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

    entry(
        databaseId = 22,
        kind = Kind.CROSS,
        section = "Diffraction",
        title = "Fraunhofer grating orders in Python and C",
        originalTitle = "Fraunhofer's ruled gratings (1821)",
        author = "Joseph von Fraunhofer",
        year = 1821,
        summary = "A grating with lines 2500 nanometres apart shows an order m only when m times the wavelength is below the spacing, and two programs count the orders for three colours.",
        details = "Fraunhofer's ruled gratings split light into orders at the angles where d sin(theta) = m lambda. The example takes a spacing of 2500 nanometres and three wavelengths, finds the largest order that can appear using whole-number arithmetic that avoids the sine, and the two programs print the same result.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### grating.py
            SPACING = 2500  # nanometres between neighbouring lines

            def max_order(wavelength):
                # The largest order m with m * wavelength < spacing, so that sin(theta) stays below one.
                m = 0
                while (m + 1) * wavelength < SPACING:
                    m += 1
                return m

            for wavelength in (500, 700, 900):
                print(f"lambda={wavelength} nm: max order {max_order(wavelength)}")
            ### grating.c
            #include <stdio.h>

            #define SPACING 2500  /* nanometres between neighbouring lines */

            static int max_order(int wavelength) {
                int m = 0;
                while ((m + 1) * wavelength < SPACING) {
                    m++;
                }
                return m;
            }

            int main(void) {
                int wavelengths[3] = {500, 700, 900};
                for (int i = 0; i < 3; i++) {
                    printf("lambda=%d nm: max order %d\n", wavelengths[i], max_order(wavelengths[i]));
                }
                return 0;
            }
            ### run.sh
            python3 grating.py > python.txt
            cc -o grating_c grating.c
            ./grating_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),
)
