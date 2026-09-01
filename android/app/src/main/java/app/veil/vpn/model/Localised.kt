package app.veil.vpn.model

import androidx.annotation.StringRes

/**
 * A piece of text the app has decided on but has not yet turned into words.
 *
 * The network layer produces plenty of text a person reads — what was measured,
 * what answered, why something failed — and it has no business holding a
 * `Context` to translate it. Passing the resource and its arguments instead
 * keeps the decision where the knowledge is and the wording where the screen
 * is, which is also what makes the result correct when the phone's language
 * changes underneath a running tunnel.
 */
data class Localised(
    @StringRes val id: Int,
    val args: List<Any> = emptyList(),
) {
    constructor(@StringRes id: Int, vararg args: Any) : this(id, args.toList())
}
