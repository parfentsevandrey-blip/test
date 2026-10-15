package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiments of 1590 to 1600: a bead-frame carry and Vieta's product for pi, each in Python and C. */
val db45Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 45,
        kind = Kind.CROSS,
        section = "Bead frames",
        title = "The suanpan carry in Python and C",
        originalTitle = "Suanfa tongzong",
        author = "Cheng Dawei",
        year = 1592,
        summary = "A bead frame's rods hold each digit as heaven and earth beads, and two programs in Python and C add 486 and 357 and print the same rod readings.",
        details = "The two programs use the same carrying rule from the units rod upward, and the run script compares their outputs line by line. The rod readings follow the bead values, a heaven bead worth five and each earth bead worth one.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### suanpan.py
            A, B = 486, 357

            def rod_digits(number):
                # Digits from the units rod upward, the order in which the beads are set.
                digits = []
                while number:
                    digits.append(number % 10)
                    number //= 10
                return digits

            def add_with_carry(a, b):
                x, y = rod_digits(a), rod_digits(b)
                result, carry = [], 0
                for i in range(max(len(x), len(y))):
                    total = (x[i] if i < len(x) else 0) + (y[i] if i < len(y) else 0) + carry
                    result.append(total % 10)
                    carry = total // 10
                if carry:
                    result.append(carry)
                return result

            digits = add_with_carry(A, B)
            print("sum", A + B)
            names = ["units", "tens", "hundreds", "thousands"]
            for i in reversed(range(len(digits))):
                d = digits[i]
                print(f"{names[i]} rod: digit {d}, heaven {d // 5}, earth {d % 5}")
            ### suanpan.c
            #include <stdio.h>

            static int rod_digits(int number, int digits[]) {
                int count = 0;
                while (number > 0) {
                    digits[count++] = number % 10;
                    number /= 10;
                }
                return count;
            }

            int main(void) {
                int a = 486, b = 357;
                int x[8], y[8], result[9];
                int nx = rod_digits(a, x);
                int ny = rod_digits(b, y);
                int n = nx > ny ? nx : ny;
                int carry = 0, count = 0;
                for (int i = 0; i < n; i++) {
                    int total = (i < nx ? x[i] : 0) + (i < ny ? y[i] : 0) + carry;
                    result[count++] = total % 10;
                    carry = total / 10;
                }
                if (carry) {
                    result[count++] = carry;
                }
                printf("sum %d\n", a + b);
                const char *names[] = {"units", "tens", "hundreds", "thousands"};
                for (int i = count - 1; i >= 0; i--) {
                    int d = result[i];
                    printf("%s rod: digit %d, heaven %d, earth %d\n", names[i], d, d / 5, d % 5);
                }
                return 0;
            }
            ### run.sh
            python3 suanpan.py > python.txt
            cc -o suanpan_c suanpan.c
            ./suanpan_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

    entry(
        databaseId = 45,
        kind = Kind.CROSS,
        section = "Series",
        title = "Vieta's product for pi in Python and C",
        originalTitle = "Variorum de rebus mathematicis responsorum liber VIII",
        author = "Francois Vieta",
        year = 1593,
        summary = "Vieta's product of nested square roots of two gives two over pi, and two programs in Python and C print the same partial products to six decimal places.",
        details = "The product appeared in Vieta's Variorum of 1593, and each factor is the square root of two plus the previous root, divided by two. The run script compares the Python and C outputs, which must agree line by line.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### vieta.py
            from math import sqrt

            # Vieta's product: 2 over pi is the product of sqrt(2 + s) / 2, where s starts at 0 and is the last root.
            s = 0.0
            product = 1.0
            for n in range(1, 6):
                s = sqrt(2 + s)
                product *= s / 2
                print(f"factors {n}: product {product:.6f}, pi estimate {2 / product:.6f}")
            ### vieta.c
            #include <math.h>
            #include <stdio.h>

            int main(void) {
                double s = 0.0, product = 1.0;
                for (int n = 1; n <= 5; n++) {
                    s = sqrt(2 + s);
                    product *= s / 2;
                    printf("factors %d: product %.6f, pi estimate %.6f\n", n, product, 2 / product);
                }
                return 0;
            }
            ### run.sh
            python3 vieta.py > python.txt
            cc -o vieta_c vieta.c -lm
            ./vieta_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
