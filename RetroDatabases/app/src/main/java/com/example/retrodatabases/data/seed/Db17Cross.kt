package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiments of 1870 to 1880: the same result from two languages, checked by a run script. */
val db17Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 17,
        kind = Kind.CROSS,
        section = "Clifford algebra",
        title = "Clifford product of two generators in Python and C",
        originalTitle = "Applications of Grassmann's extensive algebra (1878)",
        author = "William Kingdon Clifford",
        year = 1878,
        summary = "Two generators square to one and anticommute, and two programs multiply the same pair of elements in the resulting four-dimensional algebra.",
        details = "Clifford's 1878 paper combined Grassmann's extensive algebra with quaternion-like products of vectors into one algebra. The two programs multiply the same pair of elements with the same table, and the run script checks that they agree.",
        rare = true,
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### clifford.py
            # Blades of the Clifford algebra on two generators: 0 is 1, 1 is e1, 2 is e2, 3 is e1e2.
            # TABLE[(i, j)] = (sign, k) means blade i times blade j equals sign times blade k.
            TABLE = {
                (0, 0): (1, 0), (0, 1): (1, 1), (0, 2): (1, 2), (0, 3): (1, 3),
                (1, 0): (1, 1), (1, 1): (1, 0), (1, 2): (1, 3), (1, 3): (1, 2),
                (2, 0): (1, 2), (2, 1): (-1, 3), (2, 2): (1, 0), (2, 3): (-1, 1),
                (3, 0): (1, 3), (3, 1): (-1, 2), (3, 2): (1, 1), (3, 3): (-1, 0),
            }

            def multiply(x, y):
                result = [0, 0, 0, 0]
                for i in range(4):
                    for j in range(4):
                        sign, k = TABLE[(i, j)]
                        result[k] += sign * x[i] * y[j]
                return result

            x = [1, 2, 3, 0]   # 1 + 2 e1 + 3 e2
            y = [0, 1, -1, 2]  # e1 - e2 + 2 e1e2
            print("x*y coefficients on 1, e1, e2, e1e2:", multiply(x, y))
            print("e1e2 squared:", TABLE[(3, 3)])
            print("e1e2 = -e2e1:", TABLE[(1, 2)] == (1, 3) and TABLE[(2, 1)] == (-1, 3))
            ### clifford.c
            #include <stdio.h>

            /* Blade i times blade j is sgn[i][j] times blade idx[i][j]. Blades: 0 is 1, 1 is e1, 2 is e2, 3 is e1e2. */
            static const int idx[4][4] = {
                {0, 1, 2, 3},
                {1, 0, 3, 2},
                {2, 3, 0, 1},
                {3, 2, 1, 0},
            };
            static const int sgn[4][4] = {
                {1, 1, 1, 1},
                {1, 1, 1, 1},
                {1, -1, 1, -1},
                {1, -1, 1, -1},
            };

            int main(void) {
                int x[4] = {1, 2, 3, 0};
                int y[4] = {0, 1, -1, 2};
                int r[4] = {0, 0, 0, 0};
                for (int i = 0; i < 4; i++) {
                    for (int j = 0; j < 4; j++) {
                        r[idx[i][j]] += sgn[i][j] * x[i] * y[j];
                    }
                }
                printf("x*y coefficients on 1, e1, e2, e1e2: [%d, %d, %d, %d]\n", r[0], r[1], r[2], r[3]);
                printf("e1e2 squared: (%d, %d)\n", sgn[3][3], idx[3][3]);
                printf("e1e2 = -e2e1: %s\n", (sgn[1][2] == 1 && idx[1][2] == 3 && sgn[2][1] == -1 && idx[2][1] == 3) ? "True" : "False");
                return 0;
            }
            ### run.sh
            python3 clifford.py > python.txt
            cc -o clifford_c clifford.c
            ./clifford_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

    entry(
        databaseId = 17,
        kind = Kind.CROSS,
        section = "Uncountable sets",
        title = "Cantor's nested intervals in Python and C",
        originalTitle = "Uber eine Eigenschaft des Inbegriffes aller reellen algebraischen Zahlen (1874)",
        author = "Georg Cantor",
        year = 1874,
        summary = "No list of real numbers can be complete, because nested intervals can be built that skip each listed number, and two programs build the first five intervals.",
        details = "Cantor's 1874 paper showed that the real numbers cannot be put into a sequence, by building nested intervals that avoid each listed value in turn. The two programs build the first five intervals for the same list, one with exact fractions and one with whole-number endpoints, and a run script compares them.",
        rare = true,
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### cantor.py
            from fractions import Fraction

            # The listed numbers: x1 = 0, x2 = 1/2, x3 = 1/3, x4 = 2/3, x5 = 1/4.
            numbers = [Fraction(0), Fraction(1, 2), Fraction(1, 3), Fraction(2, 3), Fraction(1, 4)]

            def left_third(a, b):
                return (a, a + (b - a) / 3)

            def right_third(a, b):
                return (b - (b - a) / 3, b)

            def inside(x, interval):
                return interval[0] <= x <= interval[1]

            interval = (Fraction(0), Fraction(1))
            intervals = []
            for x in numbers:
                left = left_third(*interval)
                right = right_third(*interval)
                # The two thirds are disjoint, so keep the one that does not contain x.
                interval = left if not inside(x, left) else right
                intervals.append(interval)

            for i, (a, b) in enumerate(intervals, start=1):
                print(f"I{i} = [{a}, {b}]")
            nested = all(
                intervals[i + 1][0] >= intervals[i][0] and intervals[i + 1][1] <= intervals[i][1]
                for i in range(len(intervals) - 1)
            )
            print("each interval inside the previous one:", nested)
            print("no listed number lies in I5:", not any(inside(x, intervals[-1]) for x in numbers))
            ### cantor.c
            #include <stdio.h>

            /* Endpoints are whole numbers of units of 1/972. The listed numbers are 0, 1/2, 1/3, 2/3 and 1/4. */
            static int gcd(int a, int b) {
                while (b != 0) {
                    int t = a % b;
                    a = b;
                    b = t;
                }
                return a;
            }

            static void show(int num, int den) {
                int g = gcd(num, den);
                num /= g;
                den /= g;
                if (den == 1) {
                    printf("%d", num);
                } else {
                    printf("%d/%d", num, den);
                }
            }

            int main(void) {
                const int unit_den = 972;
                int listed[5] = {0, 486, 324, 648, 243};
                int a = 0, b = 972;
                int lo[5], hi[5];
                for (int n = 0; n < 5; n++) {
                    int d = b - a;
                    int left_hi = a + d / 3;
                    int right_lo = b - d / 3;
                    int x = listed[n];
                    if (!(x >= a && x <= left_hi)) {
                        b = left_hi;
                    } else {
                        a = right_lo;
                    }
                    lo[n] = a;
                    hi[n] = b;
                }
                for (int n = 0; n < 5; n++) {
                    printf("I%d = [", n + 1);
                    show(lo[n], unit_den);
                    printf(", ");
                    show(hi[n], unit_den);
                    printf("]\n");
                }
                int nested = 1;
                for (int n = 0; n + 1 < 5; n++) {
                    if (!(lo[n + 1] >= lo[n] && hi[n + 1] <= hi[n])) {
                        nested = 0;
                    }
                }
                printf("each interval inside the previous one: %s\n", nested ? "True" : "False");
                int clear = 1;
                for (int n = 0; n < 5; n++) {
                    if (listed[n] >= lo[4] && listed[n] <= hi[4]) {
                        clear = 0;
                    }
                }
                printf("no listed number lies in I5: %s\n", clear ? "True" : "False");
                return 0;
            }
            ### run.sh
            python3 cantor.py > python.txt
            cc -o cantor_c cantor.c
            ./cantor_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),
)
