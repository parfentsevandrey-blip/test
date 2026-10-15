package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiments of 1500 to 1510: Taxis relay stages and Euclid's greatest common measure, each in Python and C. */
val db54Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 54,
        kind = Kind.CROSS,
        section = "Communications",
        title = "Relay stages of the Taxis post in Python and C",
        originalTitle = "Taxis postal contract",
        author = "Franz von Taxis",
        year = 1505,
        summary = "A relay stage count is written once in Python and once in C, and a script checks that both print the same stages for three illustrative routes.",
        details = "The contract of 1505 put the Taxis post on a licensed footing, and each stage ended at a change of horses. The Python program rounds up with a negated floor division, the C program adds the divisor minus one before dividing, and the run script compares the two outputs line by line.",
        instructions = """
            1. Save the first program as relay.py and the second as relay.c.
            2. Run it: python3 relay.py > python.txt && cc -o relay_c relay.c && ./relay_c > c.txt && diff python.txt c.txt && cat c.txt && echo same result
        """,
        example = """
            ### relay.py
            distances = [(1260, 45), (1000, 40), (1010, 40)]
            for distance, stage in distances:
                print(distance, stage, -(-distance // stage))
            ### relay.c
            #include <stdio.h>

            int main(void) {
                int routes[3][2] = {{1260, 45}, {1000, 40}, {1010, 40}};
                for (int i = 0; i < 3; i++) {
                    int distance = routes[i][0];
                    int stage = routes[i][1];
                    int stages = (distance + stage - 1) / stage;
                    printf("%d %d %d\n", distance, stage, stages);
                }
                return 0;
            }
        """,
    ),

    entry(
        databaseId = 54,
        kind = Kind.CROSS,
        section = "Arithmetic",
        title = "Euclid's greatest common measure in Python and C",
        originalTitle = "Elements, book seven",
        author = "Euclid",
        year = 1500,
        summary = "Euclid's method for the greatest common measure of two numbers is written once in Python and once in C, and a script checks that both print the same measures for three pairs of years.",
        details = "Euclid's Elements, in book seven, finds the common measure of two numbers by repeated subtraction, and the same steps are written as remainders in the programs here. The Python and C programs use the remainder form, and the run script compares their outputs line by line.",
        instructions = """
            1. Save the first program as gcd.py and the second as gcd.c.
            2. Run it: python3 gcd.py > python.txt && cc -o gcd_c gcd.c && ./gcd_c > c.txt && diff python.txt c.txt && cat c.txt && echo same result
        """,
        example = """
            ### gcd.py
            pairs = [(1500, 1505), (1508, 1510), (1503, 1509)]
            for a, b in pairs:
                x, y = a, b
                while y:
                    x, y = y, x % y
                print(a, b, x)
            ### gcd.c
            #include <stdio.h>

            int main(void) {
                int pairs[3][2] = {{1500, 1505}, {1508, 1510}, {1503, 1509}};
                for (int i = 0; i < 3; i++) {
                    int a = pairs[i][0];
                    int b = pairs[i][1];
                    int x = a;
                    int y = b;
                    while (y != 0) {
                        int r = x % y;
                        x = y;
                        y = r;
                    }
                    printf("%d %d %d\n", a, b, x);
                }
                return 0;
            }
        """,
    ),

)
