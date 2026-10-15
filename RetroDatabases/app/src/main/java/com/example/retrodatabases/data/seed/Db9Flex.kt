package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 2026 to 2036: a signature that needs only a hash, a derivative in a few dozen lines, and a space-filling curve. */
val db9Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 9,
        kind = Kind.FLEX,
        section = "Signatures",
        title = "A hash-based one-time signature",
        originalTitle = "Constructing digital signatures from a one-way function (Lamport, 1979)",
        author = "Leslie Lamport",
        year = 2030,
        summary = "Signs a message using only a hash function: the key is 512 secret values, and each bit of the message reveals one of them.",
        details = "Hash-based signatures do not rely on factoring or elliptic curves, so a quantum computer does not break them. This version signs one message per key, and the example checks a signature and then a changed message.",
        instructions = """
            1. Save the example as lamport.py.
            2. Run it: python3 lamport.py
        """,
        example = """
            import hashlib

            def H(data):
                return hashlib.sha256(data).digest()

            def keygen(seed):
                # Two secret values per bit of the digest, derived from the seed so the example is repeatable.
                pairs = []
                for i in range(256):
                    zero = H(seed + i.to_bytes(2, "big") + b"0")
                    one = H(seed + i.to_bytes(2, "big") + b"1")
                    pairs.append((zero, one))
                public = [(H(zero), H(one)) for zero, one in pairs]
                return pairs, public

            def bits(message):
                digest = H(message)
                return [(digest[i // 8] >> (7 - i % 8)) & 1 for i in range(256)]

            def sign(secret, message):
                return [secret[i][bit] for i, bit in enumerate(bits(message))]

            def verify(public, message, signature):
                return all(H(signature[i]) == public[i][bit] for i, bit in enumerate(bits(message)))

            secret, public = keygen(b"demo seed")
            signature = sign(secret, b"pay Ann 10")
            print("valid:", verify(public, b"pay Ann 10", signature))
            print("changed message:", verify(public, b"pay Ann 1000", signature))
        """,
    ),

    entry(
        databaseId = 9,
        kind = Kind.FLEX,
        section = "Autodiff",
        title = "Reverse-mode differentiation in 30 lines",
        originalTitle = "Automatic derivative evaluation (Wengert, 1964)",
        author = "Robert Wengert",
        year = 2027,
        summary = "Records each addition and multiplication, then walks the record backwards to get the derivative of the result with respect to each input.",
        details = "This is the mechanism behind training neural networks, in miniature. The example differentiates a small expression of two inputs and prints the result and both derivatives.",
        instructions = """
            1. Save the example as autodiff.py.
            2. Run it: python3 autodiff.py
        """,
        example = """
            class Value:
                def __init__(self, data, parents=(), backward=None):
                    self.data = data
                    self.grad = 0.0
                    self.parents = parents
                    self.backward = backward

                def __add__(self, other):
                    other = other if isinstance(other, Value) else Value(other)
                    out = Value(self.data + other.data, (self, other))
                    def backward():
                        self.grad += out.grad
                        other.grad += out.grad
                    out.backward = backward
                    return out

                def __mul__(self, other):
                    other = other if isinstance(other, Value) else Value(other)
                    out = Value(self.data * other.data, (self, other))
                    def backward():
                        self.grad += other.data * out.grad
                        other.grad += self.data * out.grad
                    out.backward = backward
                    return out

                def backprop(self):
                    order, seen = [], set()
                    def visit(node):
                        if id(node) not in seen:
                            seen.add(id(node))
                            for parent in node.parents:
                                visit(parent)
                            order.append(node)
                    visit(self)
                    self.grad = 1.0
                    for node in reversed(order):
                        if node.backward:
                            node.backward()

            x = Value(3.0)
            y = Value(2.0)
            f = x * y + y * x          # f = 2xy
            f.backprop()
            print("f =", f.data)
            print("df/dx =", x.grad)
            print("df/dy =", y.grad)
        """,
    ),

    entry(
        databaseId = 9,
        kind = Kind.FLEX,
        section = "Layout",
        title = "A Hilbert curve index",
        originalTitle = "Hilbert's 1891 continuous map of a line onto a square",
        author = "David Hilbert",
        year = 2032,
        summary = "Gives each cell of a square grid a position along a curve that keeps neighbouring cells close, which helps spatial indexes and data layout.",
        details = "The example computes the index of each cell of a four by four grid and prints the grid. Consecutive indexes always sit in neighbouring cells.",
        instructions = """
            1. Save the example as hilbert.py.
            2. Run it: python3 hilbert.py
        """,
        example = """
            def xy2d(n, x, y):
                # Position of cell (x, y) along the Hilbert curve that fills an n by n grid (n a power of two).
                d = 0
                s = n // 2
                while s > 0:
                    rx = 1 if x & s else 0
                    ry = 1 if y & s else 0
                    d += s * s * ((3 * rx) ^ ry)
                    if ry == 0:
                        if rx == 1:
                            x = n - 1 - x
                            y = n - 1 - y
                        x, y = y, x
                    s //= 2
                return d

            for y in range(3, -1, -1):
                print(" ".join(f"{xy2d(4, x, y):2}" for x in range(4)))
        """,
    ),
)
