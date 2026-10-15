package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiments of 1630 to 1640: Descartes's folium traced in Python and C, and English money summed in Python and C. */
val db41Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 41,
        kind = Kind.CROSS,
        section = "Curves",
        title = "Descartes's folium of 1638, traced in Python and C",
        originalTitle = "Folium of Descartes",
        author = "Rene Descartes",
        year = 1638,
        summary = "The same three points of Descartes's folium are computed once in Python and once in C, and a script checks that both print the same figures.",
        details = "Descartes proposed the curve whose equation is x cubed plus y cubed equals three times x times y in a letter to Mersenne dated 23 August 1638, offering it as a challenge for finding tangents. The two programs compute the same points from the parameter t, and the run script shows that the printed lines agree.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### folium.py
            def point(t):
                # The folium x cubed plus y cubed equals 3xy, traced by the ratio t = y / x.
                x = 3 * t / (1 + t ** 3)
                y = 3 * t * t / (1 + t ** 3)
                return x, y

            for t in (1.0, 2.0, 0.5):
                x, y = point(t)
                print(f"t = {t}: x = {x:.4f}, y = {y:.4f}")
            ### folium.c
            #include <stdio.h>

            static void point(double t, double *x, double *y) {
                double d = 1.0 + t * t * t;
                *x = 3.0 * t / d;
                *y = 3.0 * t * t / d;
            }

            int main(void) {
                double ts[3] = {1.0, 2.0, 0.5};
                for (int i = 0; i < 3; i++) {
                    double x, y;
                    point(ts[i], &x, &y);
                    printf("t = %.1f: x = %.4f, y = %.4f\n", ts[i], x, y);
                }
                return 0;
            }
            ### run.sh
            python3 folium.py > python.txt
            cc -o folium_c folium.c
            ./folium_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

    entry(
        databaseId = 41,
        kind = Kind.CROSS,
        section = "Money",
        title = "Pounds, shillings and pence summed in Python and C",
        originalTitle = "Pounds, shillings and pence",
        author = "English accounting practice",
        year = 1635,
        summary = "English accounts of the 1630s reckoned money in pounds, shillings and pence, with twelve pence to the shilling and twenty shillings to the pound, so a column of sums has to be carried in two steps.",
        details = "The rule is the one used in English accounts through the period, and the example adds three invented sums with the carries made. Both programs print the same total, which checks that the carrying is right in both languages.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### money.py
            sums = [(2, 5, 7), (1, 17, 6), (0, 4, 9)]    # pounds, shillings, pence: invented sums

            def to_pence(pounds, shillings, pence):
                # Twenty shillings to the pound and twelve pence to the shilling.
                return pounds * 240 + shillings * 12 + pence

            total = sum(to_pence(*s) for s in sums)
            pounds, rest = divmod(total, 240)
            shillings, pence = divmod(rest, 12)
            print(f"total: {pounds} l {shillings} s {pence} d")
            ### money.c
            #include <stdio.h>

            static long to_pence(long pounds, long shillings, long pence) {
                return pounds * 240 + shillings * 12 + pence;
            }

            int main(void) {
                long sums[3][3] = {{2, 5, 7}, {1, 17, 6}, {0, 4, 9}};
                long total = 0;
                for (int i = 0; i < 3; i++) {
                    total += to_pence(sums[i][0], sums[i][1], sums[i][2]);
                }
                long pounds = total / 240;
                long rest = total % 240;
                long shillings = rest / 12;
                long pence = rest % 12;
                printf("total: %ld l %ld s %ld d\n", pounds, shillings, pence);
                return 0;
            }
            ### run.sh
            python3 money.py > python.txt
            cc -o money_c money.c
            ./money_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
