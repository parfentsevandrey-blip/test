package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 1890 to 1900: a map-colouring repair, a disk cipher, necklace counting and a film strip. */
val db15Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 15,
        kind = Kind.PROJECT,
        section = "Map colouring",
        title = "Kempe chain repair on a wheel with five colours",
        originalTitle = "Map-Colour Theorem (1890)",
        author = "Percy John Heawood",
        year = 1890,
        summary = "Heawood's 1890 repair uses a two-colour Kempe chain to free a colour for a vertex whose neighbours show all five colours.",
        details = "Kempe's 1879 argument for four colours contained an error, and Heawood showed in 1890 that a chain swap can repair a five-colouring of a map. The project starts from a wheel whose rim uses five colours, searches for a two-colour chain, swaps it and colours the hub.",
        instructions = """
            1. Save the example as kempe.py.
            2. Run it: python3 kempe.py
        """,
        example = """
            rim = ["r0", "r1", "r2", "r3", "r4"]
            colour = {"r0": 0, "r1": 1, "r2": 2, "r3": 3, "r4": 4}  # the hub has no colour yet

            def neighbours(v):
                if v == "hub":
                    return rim
                i = rim.index(v)
                return [rim[(i - 1) % 5], rim[(i + 1) % 5], "hub"]

            def chain(start, a, b):
                # Vertices reachable from start through vertices coloured a or b.
                seen, stack = {start}, [start]
                while stack:
                    v = stack.pop()
                    for w in neighbours(v):
                        if w in colour and colour[w] in (a, b) and w not in seen:
                            seen.add(w)
                            stack.append(w)
                return seen

            print("start colours: " + " ".join(f"{r}={colour[r]}" for r in rim) + " hub=none")
            a = colour["r0"]
            for x1 in ["r1", "r2", "r3", "r4"]:
                b = colour[x1]
                part = chain("r0", a, b)
                if x1 in part:
                    print(f"chain of colours {a} and {b} from r0 contains {x1}: no swap")
                    continue
                print(f"chain of colours {a} and {b} from r0: {' '.join(sorted(part))}, swapped, r0 now takes colour {b}")
                for v in part:
                    colour[v] = b if colour[v] == a else a
                break
            used = {colour[r] for r in rim}
            hub = min(c for c in range(5) if c not in used)
            colour["hub"] = hub
            print(f"hub takes colour {hub}")
            print("final colours: " + " ".join(f"{r}={colour[r]}" for r in rim) + f" hub={hub}")
            proper = all(colour[v] != colour[w] for v in colour for w in neighbours(v) if w in colour)
            print("proper colouring:", proper)
            print("colours used:", len(set(colour.values())))
        """,
    ),

    entry(
        databaseId = 15,
        kind = Kind.PROJECT,
        section = "Ciphers",
        title = "A Bazeries-style disk cipher with four disks",
        originalTitle = "Les chiffres secrets devoiles (1901)",
        author = "Etienne Bazeries",
        year = 1891,
        summary = "Each disk carries a shuffled alphabet, and a message written along one line of the disks is sent as another line, so a four-disk toy encrypts and decrypts.",
        details = "Bazeries devised his cylinder cipher in 1891 according to some accounts, and he described it in his book of 1901. This project is a four-disk toy with six letters, not Bazeries' twenty-disk cylinder, and it prints the ciphertext DFDD for the message CAFE.",
        instructions = """
            1. Save the example as bazeries.py.
            2. Run it: python3 bazeries.py
        """,
        example = """
            disks = ["ECBFDA", "BDAECF", "FAEDCB", "CFDBAE"]  # four disks, each a shuffled six-letter alphabet
            LINE = 3  # the ciphertext line is three steps round from the plaintext line

            def encrypt(message):
                # Turn each disk so the plaintext letter sits on the plaintext line, then read the ciphertext line.
                offsets = [disk.index(ch) for disk, ch in zip(disks, message)]
                return "".join(disk[(off + LINE) % 6] for disk, off in zip(disks, offsets))

            def decrypt(cipher):
                # Find each ciphertext letter on its disk and step back to the plaintext line.
                plain = []
                for disk, ch in zip(disks, cipher):
                    j = disk.index(ch)
                    plain.append(disk[(j - LINE) % 6])
                return "".join(plain)

            c = encrypt("CAFE")
            print("ciphertext " + c)
            print("decrypted " + decrypt(c))
            print("round trip: " + str(decrypt(c) == "CAFE"))
        """,
    ),

    entry(
        databaseId = 15,
        kind = Kind.PROJECT,
        section = "Counting",
        title = "Counting two-colour necklaces with Burnside's count",
        originalTitle = "Theory of Groups of Finite Order (1897)",
        author = "William Burnside",
        year = 1897,
        summary = "The number of two-colour necklaces with n beads equals the average, over the rotations, of the colourings each rotation fixes, and the program checks this by brute force.",
        details = "Burnside's 1897 book gave this averaging over a group, which carries his name, although the same counting idea appears earlier in the work of Cauchy and Frobenius. The project checks the count for n from 1 to 6 by listing every colouring and its rotations, and it prints the totals 2, 3, 4, 6, 8 and 14.",
        instructions = """
            1. Save the example as necklaces.py.
            2. Run it: python3 necklaces.py
        """,
        example = """
            from itertools import product
            from math import gcd

            def phi(m):
                # Euler's totient: how many numbers from 1 to m are coprime to m.
                return sum(1 for k in range(1, m + 1) if gcd(k, m) == 1)

            def burnside(n, colours=2):
                # Average over the n rotations of the number of colourings each rotation fixes.
                total = sum(phi(d) * colours ** (n // d) for d in range(1, n + 1) if n % d == 0)
                return total // n

            def brute(n):
                # The canonical form of a colouring is its smallest rotation; count the distinct canonical forms.
                seen = set()
                for word in product(range(2), repeat=n):
                    rotations = [word[i:] + word[:i] for i in range(n)]
                    seen.add(min(rotations))
                return len(seen)

            for n in range(1, 7):
                b, f = brute(n), burnside(n)
                print(f"n = {n}: brute force {b}, Burnside {f}, agree: {b == f}")
        """,
    ),

    entry(
        databaseId = 15,
        kind = Kind.PROJECT,
        section = "Moving pictures",
        title = "Film strip stepped frame by frame through a gate",
        originalTitle = "Cinematographe, patent of 1895",
        author = "Auguste and Louis Lumiere",
        year = 1895,
        summary = "A film strip moves one frame at a time past a shutter, and the project prints four frames of a moving bar as text to show that stepping.",
        details = "The Lumiere brothers filed for the Cinematographe in February 1895 and gave a public screening in Paris at the end of December 1895. The project steps a strip of text frames through a gate, one frame per pause, and shows that each frame holds one position of a bar moving to the right.",
        instructions = """
            1. Save the example as film_strip.py.
            2. Run it: python3 film_strip.py
        """,
        example = """
            WIDTH = 6
            frames = []
            for k in range(4):
                # Frame k shows a two-cell bar at positions k and k + 1 of the strip.
                row = "".join("#" if i in (k, k + 1) else "." for i in range(WIDTH))
                frames.append(row)
                print(f"frame {k}: {row}")
            print(f"distinct frames: {len(set(frames))}")
        """,
    ),
)
