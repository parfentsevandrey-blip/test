package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiments of 1880 to 1890: the same result from two languages, the Tower of Hanoi and a keyboard adder. */
val db16Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 16,
        kind = Kind.CROSS,
        section = "Recursion",
        title = "Tower of Hanoi moves in Python and C",
        originalTitle = "La Tour d'Hanoi",
        author = "Edouard Lucas, writing as N. Claus de Siam",
        year = 1883,
        summary = "The puzzle of moving a tower of disks is solved by the same recursive rule in Python and in C, and both print the same seven moves for three disks.",
        details = "Lucas published the puzzle in 1883 under the pen name N. Claus de Siam, with the tower moved between three pegs. The two programs use the same recursion, and the example prints each move with a count. The seven moves for three disks are the same in both languages.",
        instructions = """
            1. Save the two programs in the example as hanoi.py and hanoi.c, one file for each ### line.
            2. Run it: python3 hanoi.py, then cc -o hanoi_c hanoi.c and ./hanoi_c; the two outputs match.
        """,
        example = """
            ### hanoi.py
            def hanoi(n, source, target, spare, moves):
                # Move n disks from source to target, using spare; the largest disk moves once, in the middle.
                if n == 0:
                    return
                hanoi(n - 1, source, spare, target, moves)
                moves.append((n, source, target))
                hanoi(n - 1, spare, target, source, moves)

            moves = []
            hanoi(3, "A", "C", "B", moves)
            for k, (disk, src, dst) in enumerate(moves, start=1):
                print(f"{k}: disk {disk} {src} -> {dst}")
            print(f"moves for 3 disks: {len(moves)}")
            ### hanoi.c
            #include <stdio.h>

            static int count = 0;

            /* Move n disks from source to target through spare, printing each move. */
            static void hanoi(int n, char source, char target, char spare) {
                if (n == 0) {
                    return;
                }
                hanoi(n - 1, source, spare, target);
                count++;
                printf("%d: disk %d %c -> %c\n", count, n, source, target);
                hanoi(n - 1, spare, target, source);
            }

            int main(void) {
                hanoi(3, 'A', 'C', 'B');
                printf("moves for 3 disks: %d\n", count);
                return 0;
            }
        """,
    ),

    entry(
        databaseId = 16,
        kind = Kind.CROSS,
        section = "Adding machines",
        title = "Keyboard adder with carries in Python and C",
        originalTitle = "Key-driven adding machine",
        author = "William Seward Burroughs",
        year = 1888,
        summary = "A column-by-column adder that carries to the left like a keyboard calculator, written in Python and in C, with both programs printing the same register after each entry.",
        details = "Burroughs's key-driven adding machine, patented in August 1888, added each entry column by column and carried to the left. The two programs keep a five-column register, add five entries and print the register with the carry out. The last entry overflows the register and shows the carry.",
        instructions = """
            1. Save the two programs in the example as burroughs.py and burroughs.c, one file for each ### line.
            2. Run it: python3 burroughs.py, then cc -o burroughs_c burroughs.c and ./burroughs_c; the two outputs match.
        """,
        example = """
            ### burroughs.py
            def add_into(register, entry):
                # Add the entry column by column from the units, carrying to the next column as a keyboard adder does.
                carry = 0
                for i in range(len(register)):
                    digit = register[i] + entry[i] + carry
                    register[i] = digit % 10
                    carry = digit // 10
                return carry

            register = [0] * 5          # digits, units first
            for amount in (2735, 1468, 9999, 42, 99999):
                digits = [int(d) for d in str(amount).zfill(5)[::-1]]   # units first
                carry_out = add_into(register, digits)
                shown = "".join(str(d) for d in register[::-1])
                print(f"after {amount}: {shown} carry out {carry_out}")
            ### burroughs.c
            #include <stdio.h>

            /* Add an entry into a five-column register, units first, carrying to the left. */
            static int add_into(int reg[5], const int entry[5]) {
                int carry = 0;
                for (int i = 0; i < 5; i++) {
                    int digit = reg[i] + entry[i] + carry;
                    reg[i] = digit % 10;
                    carry = digit / 10;
                }
                return carry;
            }

            int main(void) {
                int reg[5] = {0, 0, 0, 0, 0};
                int amounts[5] = {2735, 1468, 9999, 42, 99999};
                for (int k = 0; k < 5; k++) {
                    int entry[5];
                    int value = amounts[k];
                    for (int i = 0; i < 5; i++) {
                        entry[i] = value % 10;
                        value /= 10;
                    }
                    int carry = add_into(reg, entry);
                    printf("after %d: ", amounts[k]);
                    for (int i = 4; i >= 0; i--) {
                        printf("%d", reg[i]);
                    }
                    printf(" carry out %d\n", carry);
                }
                return 0;
            }
        """,
    ),
)
