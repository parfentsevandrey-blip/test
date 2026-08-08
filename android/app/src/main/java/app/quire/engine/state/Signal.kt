package app.quire.engine.state

// A block that writes back to the signal it is reading would otherwise spin forever; eight
// rounds is more settling than any honest chain needs, and a hard floor under a mistake.
private const val MAX_ROUNDS: Int = 8

/**
 * A value that tells its readers when it changes. Enough reactivity for one app, no more.
 *
 * Main thread only: there is no locking here, because the only writer is the interface and the
 * only readers are views.
 */
class Signal<T>(initial: T) {

    // Owners and blocks are kept as two lists rather than one list of pairs so that registering
    // an observer costs no wrapper object.
    private val owners = ArrayList<Any>(4)
    private val blocks = ArrayList<(T) -> Unit>(4)

    // A block is free to subscribe or unsubscribe anything, including itself, while blocks are
    // running, so the live lists are never structurally changed mid-walk.
    private val pendingOwners = ArrayList<Any>(2)
    private val pendingBlocks = ArrayList<(T) -> Unit>(2)
    private val pendingForgets = ArrayList<Any>(2)

    private var current: T = initial
    private var dispatching = false
    private var restart = false

    /** The value itself; assigning a different one tells every observer, assigning an equal one
     * tells nobody. */
    var value: T
        get() = current
        set(next) {
            if (current == next) return
            current = next
            dispatch()
        }

    /**
     * Registers [block] under [owner] and delivers the current value immediately, so a reader
     * never has to ask separately for the state it just subscribed to. An owner may register
     * more than one block, and is matched by identity rather than equality.
     */
    fun observe(owner: Any, block: (T) -> Unit) {
        if (dispatching) {
            pendingOwners.add(owner)
            pendingBlocks.add(block)
        } else {
            owners.add(owner)
            blocks.add(block)
        }
        block(current)
    }

    /** Drops every block [owner] registered, for a view or screen that is going away. */
    fun forget(owner: Any) {
        if (dispatching) {
            pendingForgets.add(owner)
            // An owner that subscribed and then left within the same dispatch never joins the
            // live lists at all, so its queued registration goes with it.
            var i = pendingOwners.size - 1
            while (i >= 0) {
                if (pendingOwners[i] === owner) {
                    pendingOwners.removeAt(i)
                    pendingBlocks.removeAt(i)
                }
                i--
            }
            return
        }
        removeOwner(owner)
    }

    private fun dispatch() {
        if (dispatching) {
            // A block changed the value while blocks were running. The walk repeats with the
            // newer value rather than interleaving two rounds of callbacks.
            restart = true
            return
        }
        dispatching = true
        try {
            var rounds = 0
            do {
                restart = false
                flush()
                val snapshot = current
                var i = 0
                while (i < blocks.size) {
                    blocks[i](snapshot)
                    i++
                }
                rounds++
            } while (restart && rounds < MAX_ROUNDS)
        } finally {
            dispatching = false
            restart = false
            flush()
        }
    }

    private fun flush() {
        var i = 0
        while (i < pendingForgets.size) {
            removeOwner(pendingForgets[i])
            i++
        }
        pendingForgets.clear()
        i = 0
        while (i < pendingOwners.size) {
            owners.add(pendingOwners[i])
            blocks.add(pendingBlocks[i])
            i++
        }
        pendingOwners.clear()
        pendingBlocks.clear()
    }

    private fun removeOwner(owner: Any) {
        var i = owners.size - 1
        while (i >= 0) {
            if (owners[i] === owner) {
                owners.removeAt(i)
                blocks.removeAt(i)
            }
            i--
        }
    }
}

/**
 * The app's state in one place, keyed by name, so a screen can reach the state it needs without
 * being handed a chain of objects to get there.
 */
class Store {

    private val entries = LinkedHashMap<String, Signal<*>>()

    /** Every registered signal, in registration order, for saving and restoring state in bulk. */
    val signals: Map<String, Signal<*>> = entries

    /**
     * Declares the signal behind [key], or hands back the one already declared: registration is
     * where a key gets its type and its starting value, and it happens once.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> register(key: String, initial: T): Signal<T> {
        val existing = entries[key]
        if (existing != null) return existing as Signal<T>
        val created = Signal(initial)
        entries[key] = created
        return created
    }

    /** The signal behind [key], which must already have been registered. */
    @Suppress("UNCHECKED_CAST")
    fun <T> get(key: String): Signal<T> =
        (entries[key] ?: error("no signal registered for \"$key\"")) as Signal<T>
}
