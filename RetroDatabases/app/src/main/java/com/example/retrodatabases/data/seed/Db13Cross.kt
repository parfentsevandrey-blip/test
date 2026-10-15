package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiments of 1910 to 1920: the same integers from C and Python, checked by a run script. */
val db13Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 13,
        kind = Kind.CROSS,
        section = "C and Python",
        title = "C and Python compute Ramanujan's tau coefficients",
        originalTitle = "On certain arithmetical functions (Ramanujan, 1916)",
        author = "Written as a teaching example",
        year = 1916,
        summary = "Expanding q times the product of (1 - q^n) to the 24th power gives Ramanujan's tau function, and a C program and a Python program print the same first five values.",
        details = "Ramanujan's 1916 paper introduced tau(n) as the coefficient of q^n in q times the product of (1 - q^n) to the power 24. The two programs multiply truncated power series and print the coefficients of the first five terms, and the run script checks that the two outputs match, as a conformance test does.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### tau.py
            def mul(a, b, n):
                # Product of two truncated power series, keeping the coefficients of q to the power 0 up to n - 1.
                out = [0] * n
                for i, x in enumerate(a[:n]):
                    for j, y in enumerate(b[:n - i]):
                        out[i + j] += x * y
                return out


            N = 5   # coefficients of q to the power 0 to 4
            P = [1] + [0] * (N - 1)
            for k in range(1, N):
                factor = [0] * N
                factor[0] = 1
                factor[k] = -1   # the factor 1 - q^k
                for _ in range(24):
                    P = mul(P, factor, N)
            print(" ".join(str(c) for c in P))
            ### tau.c
            #include <stdio.h>

            #define N 5

            /* Product of two power series, keeping the coefficients of q to the power 0 up to N - 1. */
            static void mul(const long a[N], const long b[N], long out[N]) {
                long tmp[N] = {0};
                for (int i = 0; i < N; i++)
                    for (int j = 0; i + j < N; j++)
                        tmp[i + j] += a[i] * b[j];
                for (int i = 0; i < N; i++)
                    out[i] = tmp[i];
            }

            int main(void) {
                long P[N] = {1, 0, 0, 0, 0};
                for (int k = 1; k < N; k++) {
                    long factor[N] = {0};
                    factor[0] = 1;
                    factor[k] = -1;
                    for (int e = 0; e < 24; e++) {
                        long next[N];
                        mul(P, factor, next);
                        for (int i = 0; i < N; i++)
                            P[i] = next[i];
                    }
                }
                for (int i = 0; i < N; i++)
                    printf("%ld%s", P[i], i + 1 < N ? " " : "\n");
                return 0;
            }
            ### run.sh
            python3 tau.py > python.txt
            cc -o tau tau.c
            ./tau > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

    entry(
        databaseId = 13,
        kind = Kind.CROSS,
        section = "C and Python",
        title = "C and Python check Ramanujan's partition congruence",
        originalTitle = "Congruence properties of the partition function (Ramanujan, 1919)",
        author = "Written as a teaching example",
        year = 1919,
        summary = "The number of partitions of 5n plus 4 is a multiple of 5, and a Python program and a C program print six such values that agree.",
        details = "Ramanujan announced congruences for the partition function in 1919, among them p(5n+4) divisible by 5. The two programs count partitions with the same table of part sizes, print p(n) for n equal to 4, 9, 14, 19, 24 and 29 with a divisibility check, and the run script compares the two outputs.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### partitions.py
            def partitions(limit):
                # p[n] counts the partitions of n; each allowed part size is added in turn, as in a coin-change table.
                p = [1] + [0] * limit
                for part in range(1, limit + 1):
                    for n in range(part, limit + 1):
                        p[n] += p[n - part]
                return p


            p = partitions(30)
            for n in range(4, 31, 5):
                print(n, p[n], p[n] % 5 == 0)
            ### partitions.c
            #include <stdio.h>

            int main(void) {
                long p[31] = {0};
                p[0] = 1;
                for (int part = 1; part <= 30; part++)
                    for (int n = part; n <= 30; n++)
                        p[n] += p[n - part];
                for (int n = 4; n <= 30; n += 5)
                    printf("%d %ld %s\n", n, p[n], p[n] % 5 == 0 ? "True" : "False");
                return 0;
            }
            ### run.sh
            python3 partitions.py > python.txt
            cc -o partitions partitions.c
            ./partitions > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),
)
