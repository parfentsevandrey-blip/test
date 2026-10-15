package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 1940 to 1950: the methods of the decade, each with a small program that runs today. */
val db10Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 10,
        kind = Kind.GUIDE,
        section = "Random numbers",
        title = "How the middle-square method makes random digits",
        originalTitle = "Various techniques used in connection with random digits (von Neumann, 1951)",
        author = "John von Neumann",
        year = 1946,
        summary = "Square a four-digit number, keep the middle four digits of the square, and repeat; the digits look random for a while but can fall into a fixed point.",
        details = "Von Neumann described the method in the 1940s. Accounts date the first description to 1946 and to a 1949 talk, and the published version appeared in 1951, so 1946 is the year given here. The example prints a sequence from the seed 1111 and shows that the seed 3500 gets stuck at 2500, which is why the method is a warning as well as a demonstration.",
        instructions = """
            1. Save the example as middle_square.py.
            2. Run it: python3 middle_square.py
        """,
        example = """
            def middle_square(seed, count):
                values = []
                x = seed
                for _ in range(count):
                    square = str(x * x).zfill(8)      # the square written with eight digits
                    x = int(square[2:6])              # keep the middle four digits
                    values.append(x)
                return values

            print(" ".join(f"{v:04d}" for v in middle_square(1111, 10)))
            print(" ".join(f"{v:04d}" for v in middle_square(3500, 3)))
        """,
        rare = true,
    ),

    entry(
        databaseId = 10,
        kind = Kind.GUIDE,
        section = "Error correction",
        title = "How a Hamming code corrects one flipped bit",
        originalTitle = "Error Detecting and Error Correcting Codes (Hamming, 1950)",
        author = "Richard W. Hamming",
        year = 1950,
        summary = "Three parity bits placed among four data bits let a receiver find and fix any single flipped bit in a seven-bit word.",
        details = "The example encodes a four-bit word, flips one of its bits, finds the wrong position from the syndrome and corrects it. It then checks all 112 single-bit errors, one for each bit of each of the sixteen words. Hamming's paper appeared in the Bell System Technical Journal in April 1950.",
        instructions = """
            1. Save the example as hamming.py.
            2. Run it: python3 hamming.py
        """,
        example = """
            def encode(d):
                d1, d2, d3, d4 = d
                p1 = d1 ^ d2 ^ d4          # covers positions 1, 3, 5, 7
                p2 = d1 ^ d3 ^ d4          # covers positions 2, 3, 6, 7
                p4 = d2 ^ d3 ^ d4          # covers positions 4, 5, 6, 7
                return [p1, p2, d1, p4, d2, d3, d4]

            def syndrome(c):
                s1 = c[0] ^ c[2] ^ c[4] ^ c[6]
                s2 = c[1] ^ c[2] ^ c[5] ^ c[6]
                s4 = c[3] ^ c[4] ^ c[5] ^ c[6]
                return s4 * 4 + s2 * 2 + s1

            def correct(c):
                pos = syndrome(c)
                if pos:
                    c = c[:]
                    c[pos - 1] ^= 1
                return c

            word = [1, 0, 1, 1]
            code = encode(word)
            print("codeword:", "".join(map(str, code)))
            received = code[:]
            received[5] ^= 1
            print("received:", "".join(map(str, received)), "syndrome:", syndrome(received))
            fixed = correct(received)
            print("corrected:", "".join(map(str, fixed)), "data:", "".join(str(fixed[i]) for i in (2, 4, 5, 6)))

            all_ok = True
            for n in range(16):
                d = [(n >> k) & 1 for k in range(4)]
                good = encode(d)
                for p in range(7):
                    bad = good[:]
                    bad[p] ^= 1
                    if correct(bad) != good:
                        all_ok = False
            print("all 112 single-bit errors corrected:", all_ok)
        """,
    ),

    entry(
        databaseId = 10,
        kind = Kind.GUIDE,
        section = "Checking programs",
        title = "How a checked loop catches an off-by-one",
        originalTitle = "Checking a large routine (Turing, 1949)",
        author = "Alan Turing",
        year = 1949,
        summary = "A routine is checked by asserting what holds before each pass and by counting the passes, so a loop that runs one time too many is caught.",
        details = "Turing presented the idea at the EDSAC inaugural conference on 24 June 1949. The example multiplies by repeated addition, asserts the running total before each pass, and compares the number of passes with the expected count. The buggy version passes every assertion and still fails the final check.",
        instructions = """
            1. Save the example as checked_loop.py.
            2. Run it: python3 checked_loop.py
        """,
        example = """
            def multiply(a, b, bug=False):
                total, passes, count = 0, 0, b
                while count > 0 or (bug and count == 0):
                    # Assertion before each pass: the total is a times the passes made so far.
                    assert total == a * passes
                    total += a
                    count -= 1
                    passes += 1
                return total, passes

            def check(a, b, bug=False):
                total, passes = multiply(a, b, bug)
                ok = total == a * b and passes == b
                label = " (buggy loop)" if bug else ""
                print(f"{a} x {b}{label}: result {total}, passes {passes}, check {'passed' if ok else 'failed'}")

            check(7, 6)
            check(7, 6, bug=True)
        """,
    ),

    entry(
        databaseId = 10,
        kind = Kind.GUIDE,
        section = "Decimal arithmetic",
        title = "How an ENIAC decade ring counts and carries",
        originalTitle = "ENIAC, the Electronic Numerical Integrator and Computer (1945)",
        author = "J. Presper Eckert and John Mauchly",
        year = 1945,
        summary = "A decimal digit is a ring of ten states, each pulse moves the ring one step, and passing from nine back to zero sends a carry to the next ring.",
        details = "The example adds two numbers by sending pulses into rings, one digit at a time, and prints each digit with its carry. ENIAC's accumulators held each decimal digit in a ring of this kind, so addition was a count of pulses rather than a table lookup.",
        instructions = """
            1. Save the example as decade_ring.py.
            2. Run it: python3 decade_ring.py
        """,
        example = """
            class DecadeRing:
                def __init__(self, digit=0):
                    self.state = digit

                def pulse(self):
                    # One pulse moves the ring one step; passing from 9 back to 0 sends a carry.
                    self.state = (self.state + 1) % 10
                    return self.state == 0

            def add_digits(a, b, carry_in):
                ring = DecadeRing(a)
                carry_out = 0
                for _ in range(b + carry_in):
                    if ring.pulse():
                        carry_out = 1
                return ring.state, carry_out

            def add(x, y):
                xs = [int(c) for c in str(x)][::-1]
                ys = [int(c) for c in str(y)][::-1]
                width = max(len(xs), len(ys))
                xs += [0] * (width - len(xs))
                ys += [0] * (width - len(ys))
                carry, digits = 0, []
                for i in range(width):
                    d, carry_out = add_digits(xs[i], ys[i], carry)
                    print(f"digit {i}: {xs[i]} + {ys[i]} + {carry} = {d} carry {carry_out}")
                    digits.append(d)
                    carry = carry_out
                if carry:
                    digits.append(carry)
                return int("".join(str(d) for d in reversed(digits)))

            print("376 + 258 =", add(376, 258))
        """,
    ),

    entry(
        databaseId = 10,
        kind = Kind.GUIDE,
        section = "Neural nets",
        title = "How a McCulloch-Pitts neuron computes XOR",
        originalTitle = "A logical calculus of the ideas immanent in nervous activity (McCulloch and Pitts, 1943)",
        author = "Warren McCulloch and Walter Pitts",
        year = 1943,
        summary = "A threshold unit fires when its weighted inputs reach a threshold, and two layers of such units compute exclusive or.",
        details = "The example builds an OR unit and a NAND unit from thresholds, then combines their outputs in a third unit that acts as an AND, and prints the truth table of exclusive or. The 1943 paper showed that networks of such units can express logical functions, which linked the neuron model to logic and to computing.",
        instructions = """
            1. Save the example as xor_neuron.py.
            2. Run it: python3 xor_neuron.py
        """,
        example = """
            def neuron(inputs, weights, threshold):
                # The unit fires (1) when the weighted sum of its inputs reaches the threshold.
                return 1 if sum(i * w for i, w in zip(inputs, weights)) >= threshold else 0

            def xor(a, b):
                or_unit = neuron((a, b), (1, 1), 1)              # fires if at least one input is on
                nand_unit = neuron((a, b), (-1, -1), -1)         # fires unless both inputs are on
                return neuron((or_unit, nand_unit), (1, 1), 2)   # fires only if both units fire

            for a in (0, 1):
                for b in (0, 1):
                    print(a, b, "->", xor(a, b))
        """,
    ),
)
