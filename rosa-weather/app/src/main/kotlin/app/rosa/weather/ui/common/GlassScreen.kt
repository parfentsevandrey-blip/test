package app.rosa.weather.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.rosa.weather.R
import app.rosa.weather.core.designsystem.component.GlassIconButton
import app.rosa.weather.core.designsystem.component.RosaIcon
import app.rosa.weather.core.designsystem.theme.Rosa

/**
 * A screen over the shared sky under a large title. A screen pushed over a tab ([onBack] given)
 * also has a floating glass back button; the tabs themselves are reached from the bar below.
 */
@Composable
fun GlassScreen(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp).heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                GlassIconButton(RosaIcon.Back, stringResource(R.string.cd_back), onBack)
                Spacer(Modifier.width(14.dp))
            } else {
                Spacer(Modifier.width(6.dp))
            }
            Text(
                title,
                style = Rosa.type.title,
                color = Rosa.colors.ink,
                modifier = Modifier.weight(1f).semantics { heading() },
                maxLines = 1,
            )
            actions()
        }
        Box(Modifier.fillMaxWidth().weight(1f), content = content)
    }
}
