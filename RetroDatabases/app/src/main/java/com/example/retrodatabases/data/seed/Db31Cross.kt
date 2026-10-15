package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiments of 1730 to 1740: Stirling's group counts and the St Petersburg payout, each in Python and C. */
val db31Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 31,
        kind = Kind.CROSS,
        section = "Combinatorics",
        title = "Stirling's group counts in Python and C",
        originalTitle = "Methodus differentialis, sive tractatus de summatione et interpolatione serierum infinitarum",
        author = "James Stirling",
        year = 1730,
        summary = "The numbers of ways to split a set into groups are built by the same recurrence in Python and in C, and a script checks that the printed rows agree.",
        details = "Stirling's book of 1730 is the usual citation for the numbers that bear his name. The Python program and the C program build the same table of splittings by the recurrence, and the run script compares their printed rows.",
        instructions = """
            1. Save the Python program as stirling.py, the C program as stirling.c, and the script as run.sh.
            2. Run it: sh run.sh
        """,
        example = """
            ### stirling.py
            def stirling2(n, k):
                # S(n, k) counts the ways to split n items into k nonempty groups.
                if n == 0 and k == 0:
                    return 1
                if n == 0 or k == 0:
                    return 0
                return k * stirling2(n - 1, k) + stirling2(n - 1, k - 1)

            rows = {n: [stirling2(n, k) for k in range(1, n + 1)] for n in range(1, 6)}
            print("row 4: " + " ".join(str(x) for x in rows[4]))
            print("row 5: " + " ".join(str(x) for x in rows[5]))
            print("row sums: " + " ".join(str(sum(rows[n])) for n in range(1, 6)))
            ### stirling.c
            #include <stdio.h>

            static int s2(int n, int k) {
                /* S(n, k) by the recurrence S(n, k) = k S(n-1, k) + S(n-1, k-1). */
                if (n == 0 && k == 0) {
                    return 1;
                }
                if (n == 0 || k == 0) {
                    return 0;
                }
                return k * s2(n - 1, k) + s2(n - 1, k - 1);
            }

            int main(void) {
                for (int n = 4; n <= 5; n++) {
                    printf("row %d:", n);
                    for (int k = 1; k <= n; k++) {
                        printf(" %d", s2(n, k));
                    }
                    printf("\n");
                }
                printf("row sums:");
                for (int n = 1; n <= 5; n++) {
                    int sum = 0;
                    for (int k = 1; k <= n; k++) {
                        sum += s2(n, k);
                    }
                    printf(" %d", sum);
                }
                printf("\n");
                return 0;
            }
            ### run.sh
            python3 stirling.py > python.txt
            cc -o stirling_c stirling.c
            ./stirling_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

    entry(
        databaseId = 31,
        kind = Kind.CROSS,
        section = "Probability",
        title = "St Petersburg payout in Python and C",
        originalTitle = "Specimen theoriae novae de mensura sortis",
        author = "Daniel Bernoulli",
        year = 1738,
        summary = "The truncated payout of the St Petersburg game is summed in Python and in C, and a script checks that both programs print the same average.",
        details = "Daniel Bernoulli's paper of 1738 discusses the game whose average payout has no finite limit. The Python program and the C program sum the same payouts for ten tosses, and the run script compares their printed output.",
        instructions = """
            1. Save the Python program as petersburg.py, the C program as petersburg.c, and the script as run.sh.
            2. Run it: sh run.sh
        """,
        example = """
            ### petersburg.py
            from fractions import Fraction

            def payout(tosses):
                # Heads first on toss k pays 2 to the power k, and that toss has chance 1/2 to the power k.
                return sum(Fraction(1, 2**k) * 2**k for k in range(1, tosses + 1))

            N = 10
            print(f"expected payout with {N} tosses allowed: {payout(N)}")
            print(f"chance the game lasts past {N} tosses: {float(Fraction(1, 2**N)):.10f}")
            ### petersburg.c
            #include <stdio.h>

            int main(void) {
                /* Heads first on toss k pays 2 to the power k, with chance 1/2 to the power k. */
                int N = 10;
                double expected = 0.0;
                double chance = 1.0;
                for (int k = 1; k <= N; k++) {
                    chance *= 0.5;
                    expected += chance * (double)(1 << k);
                }
                printf("expected payout with %d tosses allowed: %.0f\n", N, expected);
                printf("chance the game lasts past %d tosses: %.10f\n", N, chance);
                return 0;
            }
            ### run.sh
            python3 petersburg.py > python.txt
            cc -o petersburg_c petersburg.c
            ./petersburg_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),
)
