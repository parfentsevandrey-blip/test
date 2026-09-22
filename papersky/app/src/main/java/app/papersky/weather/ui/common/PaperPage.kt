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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
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
import app.papersky.weather.design.DeckleShape
import app.papersky.weather.design.DiscButton
import app.papersky.weather.design.LocalChoreography
import app.papersky.weather.design.Paper
import app.papersky.weather.design.Stock
import app.papersky.weather.design.material
import app.papersky.weather.design.onSky
import app.papersky.weather.design.pressed
import app.papersky.weather.scene.SceneState
import app.papersky.weather.ui.scene.LivingScene

/**
 * Secondary screens: a strip of the living diorama on top and, below it, the table the screen's
 * things lie on — cork for places, linen for settings, kraft for widgets (DESIGN_DOCTRINE §12).
 */
@Composable
fun PaperPage(
    title: String,
    scene: SceneState,
    motion: MotionLevel,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    table: Stock = Stock.Linen,
    village: Boolean = true,
    listState: LazyListState = rememberLazyListState(),
    actions: @Composable RowScope.() -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    val colors = Paper.colors
    val lightInk = colors.onSky.red + colors.onSky.green + colors.onSky.blue > 1.5f
    Box(modifier.fillMaxSize().background(colors.sky)) {
        LivingScene(scene, Modifier.fillMaxWidth().height(290.dp), horizon = 0.66f, motion = motion, tilt = false, detail = 0.8f, laneStart = 0.6f, laneEnd = 0.94f, glass = true, village = village)
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(start = 12.dp, end = 12.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) DiscButton(PaperIcon.Back, stringResource(R.string.back), onBack)
            Spacer(Modifier.weight(1f))
            actions()
        }
        BasicText(
            title,
            Modifier.statusBarsPadding().padding(start = 22.dp, end = 22.dp, top = 64.dp).semantics { heading() },
            style = Paper.type.display.copy(color = colors.onSky, fontSize = 36.sp).onSky(lightInk),
            maxLines = 1,
        )
        Box(
            Modifier
                .fillMaxSize()
                .padding(top = 124.dp)
                .statusBarsPadding()
                .material(table, DeckleShape(seed = table.ordinal * 7 + 3, corner = 26.dp, roughness = 1.2.dp), level = 3),
        ) {
            CompositionLocalProvider(LocalChoreography provides remember { Choreography() }) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp, end = 16.dp, top = 24.dp,
                        bottom = WindowInsets.navigationBars.union(WindowInsets.ime).asPaddingValues().calculateBottomPadding() + 28.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    content = content,
                )
            }
        }
    }
}

/** A section title printed straight onto the table's material. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, onDark: Boolean = false) {
    BasicText(
        text.uppercase(),
        modifier.padding(top = 10.dp, start = 6.dp, bottom = 2.dp).semantics { heading() },
        style = Paper.type.label.copy(color = if (onDark) Paper.colors.paper.copy(alpha = 0.9f) else Paper.colors.paperInk.copy(alpha = 0.78f)).pressed(onDark),
    )
}

@Composable
fun backLabel(): String = stringResource(R.string.back)
