package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiments of 1830 to 1840: Mobius's function and Gauss's Gaussian primes, each in Python and in C. */
val db21Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 21,
        kind = Kind.CROSS,
        section = "Number theory",
        title = "Mobius's function in Python and C",
        originalTitle = "Uber eine besondere Art von Umkehrung der Reihen (1832)",
        author = "August Ferdinand Mobius",
        year = 1832,
        summary = "The Mobius function gives 1, minus 1 or 0 for each number, and its sum over the divisors of n is 1 for n equal to 1 and 0 for every larger n.",
        details = "Mobius introduced his inversion formula and the function in 1832, to invert sums taken over the divisors of a number. The two programs compute the function for 1 to 12 by factoring, once in Python and once in C, and then check the sums over divisors. Both print the same output. The record uses the 1832 paper date.",
        instructions = """
            1. Save the first program as mobius.py and the second as mobius.c.
            2. Run it: python3 mobius.py, then gcc -O2 -o mobius_c mobius.c && ./mobius_c
        """,
        example = """
            ### mobius.py
            def mobius(n):
                value = 1
                x = n
                p = 2
                while p * p <= x:
                    if x % p == 0:
                        count = 0
                        while x % p == 0:
                            x //= p
                            count += 1
                        if count > 1:
                            return 0
                        value = -value
                    p += 1
                if x > 1:
                    value = -value
                return value

            mu = [mobius(n) for n in range(1, 13)]
            print("mu:", " ".join(str(v) for v in mu))
            sums = [sum(mobius(d) for d in range(1, n + 1) if n % d == 0) for n in range(1, 13)]
            print("divisor sums:", " ".join(str(s) for s in sums))
            ### mobius.c
            #include <stdio.h>

            static int mobius(int n) {
                int value = 1;
                int x = n;
                for (int p = 2; p * p <= x; p++) {
                    if (x % p == 0) {
                        int count = 0;
                        while (x % p == 0) {
                            x /= p;
                            count++;
                        }
                        if (count > 1) return 0;
                        value = -value;
                    }
                }
                if (x > 1) value = -value;
                return value;
            }

            int main(void) {
                int mu[13];
                for (int n = 1; n <= 12; n++) mu[n] = mobius(n);
                printf("mu:");
                for (int n = 1; n <= 12; n++) printf(" %d", mu[n]);
                printf("\n");
                printf("divisor sums:");
                for (int n = 1; n <= 12; n++) {
                    int s = 0;
                    for (int d = 1; d <= n; d++)
                        if (n % d == 0) s += mu[d];
                    printf(" %d", s);
                }
                printf("\n");
                return 0;
            }
        """,
    ),

    entry(
        databaseId = 21,
        kind = Kind.CROSS,
        section = "Gaussian integers",
        title = "Gaussian primes in Python and C",
        originalTitle = "Theoria residuorum biquadraticorum commentatio secunda (1832)",
        author = "Carl Friedrich Gauss",
        year = 1832,
        summary = "Gaussian integers a plus b i factor much as ordinary integers do, and the programs list the Gaussian primes with small coordinates and norm at most 25.",
        details = "Gauss introduced the Gaussian integers in his second memoir on biquadratic residues, printed in 1832, to study prime factors in this ring. The two programs test each point of a small grid for divisibility and print the primes they find, once in Python and once in C. The record uses the 1832 date.",
        instructions = """
            1. Save the first program as gaussian.py and the second as gaussian.c.
            2. Run it: python3 gaussian.py, then gcc -O2 -o gaussian_c gaussian.c && ./gaussian_c
        """,
        example = """
            ### gaussian.py
            def norm(z):
                return z[0] * z[0] + z[1] * z[1]

            def divides(w, z):
                # w divides z when z times the conjugate of w is a multiple of the norm of w
                a, b = w
                c, d = z
                n = norm(w)
                re = c * a + d * b
                im = d * a - c * b
                return re % n == 0 and im % n == 0

            def is_gaussian_prime(z):
                n = norm(z)
                if n <= 1:
                    return False
                for a in range(-6, 7):
                    for b in range(-6, 7):
                        w = (a, b)
                        nw = norm(w)
                        if 1 < nw < n and n % nw == 0 and divides(w, z):
                            return False
                return True

            primes = [(a, b) for a in range(0, 6) for b in range(0, 6) if norm((a, b)) <= 25 and is_gaussian_prime((a, b))]
            print("Gaussian primes with a >= 0, b >= 0 and norm at most 25:")
            print(" ".join(f"{a}+{b}i" for a, b in primes))
            ### gaussian.c
            #include <stdio.h>

            static int norm(int a, int b) {
                return a * a + b * b;
            }

            /* w divides z when z times the conjugate of w is a multiple of the norm of w */
            static int divides(int wa, int wb, int za, int zb) {
                int n = norm(wa, wb);
                int re = za * wa + zb * wb;
                int im = zb * wa - za * wb;
                return re % n == 0 && im % n == 0;
            }

            static int is_gaussian_prime(int a, int b) {
                int n = norm(a, b);
                if (n <= 1) return 0;
                for (int wa = -6; wa <= 6; wa++) {
                    for (int wb = -6; wb <= 6; wb++) {
                        int nw = norm(wa, wb);
                        if (nw > 1 && nw < n && n % nw == 0 && divides(wa, wb, a, b)) return 0;
                    }
                }
                return 1;
            }

            int main(void) {
                int first = 1;
                printf("Gaussian primes with a >= 0, b >= 0 and norm at most 25:\n");
                for (int a = 0; a < 6; a++) {
                    for (int b = 0; b < 6; b++) {
                        if (norm(a, b) <= 25 && is_gaussian_prime(a, b)) {
                            printf("%s%d+%di", first ? "" : " ", a, b);
                            first = 0;
                        }
                    }
                }
                printf("\n");
                return 0;
            }
        """,
    ),
)
