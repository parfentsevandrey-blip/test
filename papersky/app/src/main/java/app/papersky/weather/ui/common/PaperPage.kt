package app.papersky.weather.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.papersky.weather.R
import app.papersky.weather.core.model.MotionLevel
import app.papersky.weather.design.Choreography
import app.papersky.weather.design.Label
import app.papersky.weather.design.LocalChoreography
import app.papersky.weather.design.Paper
import app.papersky.weather.design.PaperDisc
import app.papersky.weather.design.PaperRule
import app.papersky.weather.design.onSky
import app.papersky.weather.design.paperSurface
import app.papersky.weather.scene.SceneState
import app.papersky.weather.ui.scene.LivingScene

private val PageShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)

/**
 * Secondary screens (DESIGN_DOCTRINE §12): a strip of the living print on top, the title set in
 * the serif on it, and a large sheet of cotton paper laid over it (level 4, radius 24 dp).
 */
@Composable
fun PaperPage(
    title: String,
    scene: SceneState,
    motion: MotionLevel,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    village: Boolean = true,
    listState: LazyListState = rememberLazyListState(),
    actions: @Composable RowScope.() -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    val colors = Paper.colors
    val lightInk = colors.onSky.red + colors.onSky.green + colors.onSky.blue > 1.5f
    Box(modifier.fillMaxSize().background(colors.sky)) {
        LivingScene(scene, Modifier.fillMaxWidth().height(300.dp), horizon = 0.72f, motion = motion, tilt = false, detail = 0.7f, laneStart = 0.6f, laneEnd = 0.94f, glass = true, village = village)
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                PaperDisc(PaperIcon.Back, stringResource(R.string.back), onBack)
                Spacer(Modifier.width(14.dp))
            }
            BasicText(
                title,
                Modifier.weight(1f).semantics { heading() },
                style = Paper.type.display.copy(color = colors.onSky).onSky(lightInk),
                maxLines = 1,
            )
            actions()
        }
        Box(
            Modifier
                .fillMaxSize()
                .padding(top = 118.dp)
                .statusBarsPadding()
                .paperSurface(PageShape, level = 4),
        ) {
            CompositionLocalProvider(LocalChoreography provides remember { Choreography() }) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp, end = 16.dp, top = 24.dp,
                        bottom = WindowInsets.navigationBars.union(WindowInsets.ime).asPaddingValues().calculateBottomPadding() + 28.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = content,
                )
            }
        }
    }
}

/** A section title: small capitals and a hairline rule running out to the margin (§8). */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(top = 16.dp, bottom = 2.dp, start = 4.dp, end = 4.dp).semantics { heading() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Label(text, color = Paper.colors.paperInk)
        Spacer(Modifier.width(12.dp))
        PaperRule(Modifier.weight(1f))
    }
}

/** A short hint in the human voice: the italic, quiet (§6 `note`). */
@Composable
fun Hint(text: String, modifier: Modifier = Modifier) {
    BasicText(text, modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), style = Paper.type.note.copy(color = Paper.colors.paperInkSoft, fontSize = 18.sp))
}

@Composable
fun backLabel(): String = stringResource(R.string.back)
