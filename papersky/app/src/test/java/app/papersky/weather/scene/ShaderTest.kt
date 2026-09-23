package app.papersky.weather.scene

import android.graphics.RuntimeShader
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.papersky.weather.TestApp
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Every shader compiles with the platform's AGSL compiler and exposes the uniforms we set. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [37], application = TestApp::class)
class ShaderTest {
    @Test
    fun fireCompiles() {
        val s = RuntimeShader(Shaders.FIRE)
        s.setFloatUniform("size", 300f, 200f)
        s.setFloatUniform("time", 1f)
        s.setFloatUniform("intensity", 1f)
        s.setFloatUniform("lean", 0f)
        s.setFloatUniform("flare", 0f)
    }

    @Test
    fun fogCompiles() {
        val s = RuntimeShader(Shaders.FOG)
        s.setFloatUniform("size", 300f, 200f)
        s.setFloatUniform("time", 1f)
        s.setFloatUniform("density", 0.5f)
        s.setFloatUniform("horizon", 100f)
        s.setFloatUniform("depth", 80f)
        s.setFloatUniform("tint", 1f, 1f, 1f, 1f)
    }

    @Test
    fun waterCompiles() {
        val s = RuntimeShader(Shaders.WATER)
        s.setFloatUniform("size", 300f, 200f)
        s.setFloatUniform("time", 1f)
        s.setFloatUniform("strength", 1f)
        s.setFloatUniform("light", 1f, 1f, 1f, 0.5f)
        s.setFloatUniform("shade", 0f, 0f, 0f, 0.5f)
    }
}
