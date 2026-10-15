package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 1910 to 1920: the techniques behind the decade's results, each with a small program that runs today. */
val db13Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 13,
        kind = Kind.GUIDE,
        section = "Estimation",
        title = "How maximum likelihood picks a parameter",
        originalTitle = "On an absolute criterion for fitting frequency curves (Fisher, 1912)",
        author = "Ronald Aylmer Fisher",
        year = 1912,
        summary = "Fisher's 1912 criterion picks the parameter that makes the observed data most probable, which for seven heads in ten tosses is p = 0.7.",
        details = "Fisher's first paper on the method appeared in 1912 in the Messenger of Mathematics. The example takes seven heads in ten tosses, writes the likelihood as p to the seventh power times (1 - p) cubed, and searches a grid of probabilities for its largest value. The grid search lands on 0.70, the observed fraction, as setting the derivative to zero predicts.",
        instructions = """
            1. Save the example as mle.py.
            2. Run it: python3 mle.py
        """,
        example = """
            import math

            heads, tosses = 7, 10


            def log_likelihood(p):
                # Log of p to the power of the heads, times (1 - p) to the power of the tails.
                return heads * math.log(p) + (tosses - heads) * math.log(1 - p)


            grid = [k / 100 for k in range(1, 100)]
            best = max(grid, key=log_likelihood)
            print(f"best p on the grid: {best:.2f}")
            print(f"log-likelihood at best: {log_likelihood(best):.4f}")
            print(f"heads/tosses: {heads / tosses:.2f}")
        """,
    ),

    entry(
        databaseId = 13,
        kind = Kind.GUIDE,
        section = "Ciphers",
        title = "How a one-time tape cipher hides a message",
        originalTitle = "US patent 1,310,719 on a teleprinter cipher (Vernam, issued 1919)",
        author = "Gilbert Vernam",
        year = 1919,
        summary = "Each character of a message is combined with a character of a key tape by exclusive or, and the same tape decrypts it, so the cipher is unbreakable when the tape is random and used once.",
        details = "Vernam's 1919 patent describes a teleprinter that combines each Baudot bit of a message with a bit from a key tape. The operation is exclusive or, which the patent does not name. Reusing a key tape lets an attacker cancel the key by combining two ciphertexts, and the example shows that. Sources disagree on the filing year, given as 1915 or 1918, so this record uses the year the patent was issued.",
        instructions = """
            1. Save the example as vernam.py.
            2. Run it: python3 vernam.py
        """,
        example = """
            def xor_bytes(data, key):
                return bytes(d ^ k for d, k in zip(data, key))


            key = bytes([0x5A, 0x3C])   # one key byte for each message byte, used once
            first = b"HI"
            second = b"HO"
            c1 = xor_bytes(first, key)
            c2 = xor_bytes(second, key)
            print("cipher:", c1.hex())
            print("decrypted:", xor_bytes(c1, key).decode())
            print("reused key, cipher XOR:", xor_bytes(c1, c2).hex())
            print("plaintext XOR:", xor_bytes(first, second).hex())
        """,
    ),

    entry(
        databaseId = 13,
        kind = Kind.GUIDE,
        section = "Ciphers",
        title = "How the ADFGVX cipher works, step by step",
        originalTitle = "ADFGVX field cipher of the German army (Nebel, 1918)",
        author = "Fritz Nebel",
        year = 1918,
        summary = "A six-by-six square turns each letter into a pair of ADFGVX letters, and a columnar transposition then scrambles the pairs, which made the German field cipher hard to read in 1918.",
        details = "Fritz Nebel's cipher entered German army use in March 1918. The six-letter form, in use from 1 June 1918, was broken by Georges Painvin in early June. The example uses a plain square with no keyword, so each stage is visible: it fractionates ATTACK, transposes the result with the key CAB, then reverses both stages to recover the message.",
        instructions = """
            1. Save the example as adfgvx.py.
            2. Run it: python3 adfgvx.py
        """,
        example = """
            ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
            LABELS = "ADFGVX"


            def fractionate(text):
                # Each character becomes two labels: its row, then its column in the 6 by 6 square.
                out = []
                for ch in text:
                    i = ALPHABET.index(ch)
                    out.append(LABELS[i // 6] + LABELS[i % 6])
                return "".join(out)


            def transpose(text, key):
                # Write the text in rows under the key, then read the columns in alphabetical order of the key letters.
                columns = [text[c::len(key)] for c in range(len(key))]
                order = sorted(range(len(key)), key=lambda c: key[c])
                return "".join(columns[c] for c in order)


            def untranspose(text, key):
                n = len(key)
                rows = len(text) // n
                order = sorted(range(n), key=lambda c: key[c])
                columns = {}
                pos = 0
                for c in order:
                    columns[c] = text[pos:pos + rows]
                    pos += rows
                return "".join(columns[c][r] for r in range(rows) for c in range(n))


            def defractionate(text):
                out = []
                for i in range(0, len(text), 2):
                    row = LABELS.index(text[i])
                    col = LABELS.index(text[i + 1])
                    out.append(ALPHABET[row * 6 + col])
                return "".join(out)


            key = "CAB"
            fractions = fractionate("ATTACK")
            cipher = transpose(fractions, key)
            print("fractionated:", fractions)
            print("cipher:", cipher)
            print("recovered:", defractionate(untranspose(cipher, key)))
        """,
    ),

    entry(
        databaseId = 13,
        kind = Kind.GUIDE,
        section = "Signals",
        title = "How the Haar transform splits a signal",
        originalTitle = "Zur Theorie der orthogonalen Funktionensysteme (Haar, 1910)",
        author = "Alfred Haar",
        year = 1910,
        summary = "The Haar transform replaces each pair of values by their average and half difference and repeats on the averages, so a signal splits into one coarse average and finer details.",
        details = "Haar's 1910 paper on orthogonal function systems on the interval [0,1] contains the system now called the Haar system, which is usually cited as the first wavelet basis. The example applies the step three times to an eight-value signal, prints the detail coefficients from coarse to fine, and rebuilds the signal exactly from them.",
        instructions = """
            1. Save the example as haar.py.
            2. Run it: python3 haar.py
        """,
        example = """
            def haar(values):
                # Replace each pair by its average and its half difference, then repeat on the averages.
                details = []
                current = list(values)
                while len(current) > 1:
                    averages = [(current[i] + current[i + 1]) // 2 for i in range(0, len(current), 2)]
                    diffs = [(current[i] - current[i + 1]) // 2 for i in range(0, len(current), 2)]
                    details.append(diffs)
                    current = averages
                return current[0], details


            def inverse(average, details):
                # Undo the steps from the coarsest level down: each average splits into average plus and minus its difference.
                current = [average]
                for diffs in reversed(details):
                    rebuilt = []
                    for a, d in zip(current, diffs):
                        rebuilt.extend([a + d, a - d])
                    current = rebuilt
                return current


            signal = [8, 6, 2, 4, 10, 2, 6, 10]
            average, details = haar(signal)
            print("average:", average)
            for level, diffs in enumerate(reversed(details), start=1):
                print(f"detail level {level}:", diffs)
            print("reconstructed equals input:", inverse(average, details) == signal)
        """,
    ),

    entry(
        databaseId = 13,
        kind = Kind.GUIDE,
        section = "Primes",
        title = "How Pocklington's test proves a number prime",
        originalTitle = "The determination of the prime or composite nature of large numbers by Fermat's theorem (Pocklington, 1914)",
        author = "Henry Cabourn Pocklington",
        year = 1914,
        summary = "If a factored part of n minus 1 is larger than the square root of n, and a base passes the test for each of its prime factors, then n is prime, without factoring all of n minus 1.",
        details = "Pocklington presented the method in 1914 in a paper on the determination of prime or composite nature by Fermat's theorem. The example certifies 101 from the factors of 100, and shows that 91 is composite because base 2 fails the Fermat congruence. Taking only the factors 2 and 5 of 100, the product 10 is too small for 101, and the program reports that.",
        rare = true,
        instructions = """
            1. Save the example as pocklington.py.
            2. Run it: python3 pocklington.py
        """,
        example = """
            import math


            def certify(n, factors):
                # factors maps each prime q dividing n - 1 to its exponent; F is the product of the known prime powers.
                f = 1
                for q, e in factors.items():
                    f *= q ** e
                if f * f <= n:
                    return "not enough factors"
                for a in range(2, 20):
                    if pow(a, n - 1, n) != 1:
                        return f"composite: base {a} gives {a}^{n - 1} mod {n} = {pow(a, n - 1, n)}"
                    if all(math.gcd(pow(a, (n - 1) // q, n) - 1, n) == 1 for q in factors):
                        return f"prime, certified with base {a}"
                return "no certificate found"


            known = {2: 2, 5: 2}
            print(f"101: {certify(101, known)}")
            print("101 with F = 10: " + certify(101, {2: 1, 5: 1}))
            print(f"91: {certify(91, {2: 1, 3: 2, 5: 1})}")
        """,
    ),
)
