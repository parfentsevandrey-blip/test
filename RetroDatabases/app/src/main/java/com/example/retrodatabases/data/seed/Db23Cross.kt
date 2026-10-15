package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiments of 1810 to 1820: the same result from two languages, checked by a run script. */
val db23Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 23,
        kind = Kind.CROSS,
        section = "Polynomials",
        title = "Horner's evaluation in Python and C",
        originalTitle = "A new method of solving numerical equations of all orders, by continuous approximation (1819)",
        author = "William George Horner",
        year = 1819,
        summary = "Horner's nested rule evaluates a cubic in Python and in C, and the two programs print the same values, which the run script compares.",
        details = "Horner's nested scheme needs one multiplication and one addition per coefficient, and it is the same rule in both languages. The two programs evaluate 2x cubed minus 6x squared plus 2x minus 1 at five points and print the values, and the run script checks that the outputs match.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### horner.py
            coeffs = [2, -6, 2, -1]   # 2x^3 - 6x^2 + 2x - 1, highest power first

            def horner(cs, x):
                value = 0
                for c in cs:
                    value = value * x + c
                return value

            for x in (0, 1, 2, 3, -1):
                print(f"p({x}) = {horner(coeffs, x)}")
            ### horner.c
            #include <stdio.h>

            /* Horner evaluation of 2x^3 - 6x^2 + 2x - 1, highest power first. */
            static long horner(const long *cs, int n, long x) {
                long value = 0;
                for (int i = 0; i < n; i++) {
                    value = value * x + cs[i];
                }
                return value;
            }

            int main(void) {
                long coeffs[] = {2, -6, 2, -1};
                long xs[] = {0, 1, 2, 3, -1};
                for (int k = 0; k < 5; k++) {
                    printf("p(%ld) = %ld\n", xs[k], horner(coeffs, 4, xs[k]));
                }
                return 0;
            }
            ### run.sh
            python3 horner.py > py.txt
            gcc -o horner_c horner.c && ./horner_c > c.txt
            cat py.txt
            if cmp -s py.txt c.txt; then echo "same result"; else echo "different results"; fi
        """,
        rare = true,
    ),

    entry(
        databaseId = 23,
        kind = Kind.CROSS,
        section = "Analysis",
        title = "Bolzano's bisection with integer midpoints in Python and C",
        originalTitle = "Rein analytischer Beweis des Lehrsatzes (1817)",
        author = "Bernard Bolzano",
        year = 1817,
        summary = "Bolzano's halving runs on integer midpoints in Python and in C, so both languages print the same sign pattern with no floating-point rounding.",
        details = "Scaling every point by 64 turns the interval into whole numbers, and the sign of the function is read from the sign of the midpoint squared minus two times 64 squared. The two programs print six steps and the final bracket, and the run script compares them.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### bolzano.py
            SCALE = 64
            lo, hi = 1 * SCALE, 2 * SCALE
            for step in range(1, 7):
                mid = (lo + hi) // 2
                negative = mid * mid - 2 * SCALE * SCALE < 0
                sign = "negative" if negative else "positive"
                print(f"step {step}: midpoint {mid}/{SCALE}, sign {sign}")
                if negative:
                    lo = mid
                else:
                    hi = mid
            print(f"root between {lo}/{SCALE} and {hi}/{SCALE}")
            print(f"width {hi - lo}/{SCALE}")
            ### bolzano.c
            #include <stdio.h>

            int main(void) {
                const int scale = 64;
                int lo = 1 * scale, hi = 2 * scale;
                for (int step = 1; step <= 6; step++) {
                    int mid = (lo + hi) / 2;
                    int negative = mid * mid - 2 * scale * scale < 0;
                    printf("step %d: midpoint %d/%d, sign %s\n", step, mid, scale, negative ? "negative" : "positive");
                    if (negative) {
                        lo = mid;
                    } else {
                        hi = mid;
                    }
                }
                printf("root between %d/%d and %d/%d\n", lo, scale, hi, scale);
                printf("width %d/%d\n", hi - lo, scale);
                return 0;
            }
            ### run.sh
            python3 bolzano.py > py.txt
            gcc -o bolzano_c bolzano.c && ./bolzano_c > c.txt
            cat py.txt
            if cmp -s py.txt c.txt; then echo "same result"; else echo "different results"; fi
        """,
    ),
)
