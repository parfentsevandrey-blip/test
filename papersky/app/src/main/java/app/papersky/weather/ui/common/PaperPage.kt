package app.papersky.weather.ui.common

import androidx.compose.foundation.background
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.papersky.weather.design.Choreography
import app.papersky.weather.design.LocalChoreography
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.papersky.weather.R
import app.papersky.weather.core.model.MotionLevel
import app.papersky.weather.design.Paper
import app.papersky.weather.design.paperSheet
import app.papersky.weather.design.pressable
import app.papersky.weather.scene.SceneState
import app.papersky.weather.ui.scene.LivingScene

/**
 * Secondary screens: a strip of the living sky on top and a large sheet of paper laid over it,
 * holding the content.
 */
@Composable
fun PaperPage(
    title: String,
    scene: SceneState,
    motion: MotionLevel,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    actions: @Composable RowScope.() -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    val colors = Paper.colors
    Box(modifier.fillMaxSize().background(colors.sky)) {
        LivingScene(scene, Modifier.fillMaxWidth().height(280.dp), horizon = 0.7f, motion = motion, tilt = false, detail = 0.7f, laneStart = 0.6f, laneEnd = 0.94f)
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(start = 12.dp, end = 12.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                Box(
                    Modifier
                        .semantics { contentDescription = "back" }
                        .pressable(onBack, pressed = 0.88f)
                        .size(44.dp)
                        .paperSheet(colors.paper, CircleShape, colors.shadow, lift = 5.dp, night = colors.isNight),
                    contentAlignment = Alignment.Center,
                ) { PaperIconView(PaperIcon.Back, colors.paperInk, size = 22.dp) }
            }
            Spacer(Modifier.weight(1f))
            actions()
        }
        BasicText(
            title,
            Modifier
                .statusBarsPadding()
                .padding(start = 24.dp, end = 24.dp, top = 66.dp)
                .semantics { heading() },
            style = Paper.type.display.copy(color = colors.onSky, fontSize = 38.sp, fontWeight = FontWeight(300)),
            maxLines = 1,
        )
        Box(
            Modifier
                .fillMaxSize()
                .padding(top = 128.dp)
                .statusBarsPadding()
                .paperSheet(colors.paper, SheetShape, colors.shadow, lift = 16.dp, night = colors.isNight),
        ) {
            CompositionLocalProvider(LocalChoreography provides remember { Choreography() }) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp, end = 16.dp, top = 22.dp,
                        bottom = WindowInsets.navigationBars.union(WindowInsets.ime).asPaddingValues().calculateBottomPadding() + 28.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    content = content,
                )
            }
        }
    }
}

private val SheetShape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    BasicText(text.uppercase(), modifier.padding(top = 12.dp, start = 6.dp, bottom = 2.dp).semantics { heading() }, style = Paper.type.label.copy(color = Paper.colors.accent))
}

@Composable
fun backLabel(): String = stringResource(R.string.back)
