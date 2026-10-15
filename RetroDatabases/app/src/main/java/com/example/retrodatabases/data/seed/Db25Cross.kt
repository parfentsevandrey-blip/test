package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiments of 1790 to 1800: decimal clock time and Legendre's three-square theorem, each in two languages. */
val db25Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 25,
        kind = Kind.CROSS,
        section = "Clock time",
        title = "Decimal time in Python and C",
        originalTitle = "Decimal day of ten hours, decreed in 1793",
        author = "French National Convention",
        year = 1793,
        summary = "A decimal day has ten hours of a hundred minutes of a hundred seconds, and two programs convert clock times to it and agree.",
        details = "The decimal day was decreed in 1793, and a decimal second is 0.864 ordinary seconds long. The Python and C programs convert five clock times to decimal time with the same integer arithmetic, and the run script compares their output.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### dectime.py
            TIMES = [(0, 0, 0), (3, 36, 0), (6, 0, 0), (12, 0, 0), (18, 0, 0)]
            for hour, minute, second in TIMES:
                seconds = hour * 3600 + minute * 60 + second
                dec = seconds * 100000 // 86400
                print(f"{hour:02d}:{minute:02d}:{second:02d} = {dec // 10000}h {(dec % 10000) // 100}m {dec % 100}s")
            ### dectime.c
            #include <stdio.h>

            int main(void) {
                int times[5][3] = {{0, 0, 0}, {3, 36, 0}, {6, 0, 0}, {12, 0, 0}, {18, 0, 0}};
                for (int i = 0; i < 5; i++) {
                    long long seconds = times[i][0] * 3600LL + times[i][1] * 60LL + times[i][2];
                    long long dec = seconds * 100000LL / 86400LL;
                    printf("%02d:%02d:%02d = %lldh %lldm %llds\n", times[i][0], times[i][1], times[i][2],
                           dec / 10000, (dec % 10000) / 100, dec % 100);
                }
                return 0;
            }
            ### run.sh
            python3 dectime.py > python.txt
            cc -o dectime_c dectime.c
            ./dectime_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

    entry(
        databaseId = 25,
        kind = Kind.CROSS,
        section = "Sums of squares",
        title = "Legendre's three-square theorem in Python and C",
        originalTitle = "Essai sur la theorie des nombres (1798)",
        author = "Adrien-Marie Legendre",
        year = 1798,
        summary = "A whole number is a sum of three squares unless it has the form four to a power times 8k plus 7, and two programs check the numbers up to 30 against that form.",
        details = "Legendre stated the three-square theorem in his 1798 Essai. The Python and C programs search for three squares for each number up to 30, list the numbers with no such sum, and compare the list with the excluded form.",
        rare = true,
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### squares.py
            LIMIT = 30

            def three_squares(n):
                for x in range(6):
                    for y in range(6):
                        for z in range(6):
                            if x * x + y * y + z * z == n:
                                return True
                return False

            def excluded(n):
                while n % 4 == 0:
                    n //= 4
                return n % 8 == 7

            missing = [n for n in range(1, LIMIT + 1) if not three_squares(n)]
            print("not sums of three squares up to 30: " + " ".join(str(n) for n in missing))
            print("count: " + str(len(missing)))
            print("formula check: " + str(missing == [n for n in range(1, LIMIT + 1) if excluded(n)]))
            ### squares.c
            #include <stdio.h>

            static int three_squares(int n) {
                for (int x = 0; x < 6; x++) {
                    for (int y = 0; y < 6; y++) {
                        for (int z = 0; z < 6; z++) {
                            if (x * x + y * y + z * z == n) {
                                return 1;
                            }
                        }
                    }
                }
                return 0;
            }

            static int excluded(int n) {
                while (n % 4 == 0) {
                    n /= 4;
                }
                return n % 8 == 7;
            }

            int main(void) {
                int limit = 30;
                int count = 0;
                int formula_ok = 1;
                printf("not sums of three squares up to 30:");
                for (int n = 1; n <= limit; n++) {
                    int missing = !three_squares(n);
                    if (missing) {
                        printf(" %d", n);
                        count++;
                    }
                    if (missing != excluded(n)) {
                        formula_ok = 0;
                    }
                }
                printf("\n");
                printf("count: %d\n", count);
                printf("formula check: %s\n", formula_ok ? "True" : "False");
                return 0;
            }
            ### run.sh
            python3 squares.py > python.txt
            cc -o squares_c squares.c
            ./squares_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),
)
