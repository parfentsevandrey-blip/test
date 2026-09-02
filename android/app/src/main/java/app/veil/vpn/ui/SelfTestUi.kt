package app.veil.vpn.ui

/** What the diagnostics screen shows about a self-test run. */
sealed interface SelfTestUi {
    data object Idle : SelfTestUi

    /** [percent] 0..100, [label] the stage currently running. */
    data class Running(val percent: Int, val label: String) : SelfTestUi

    /** The finished report, ready to read and copy. */
    data class Done(val report: String) : SelfTestUi
}
