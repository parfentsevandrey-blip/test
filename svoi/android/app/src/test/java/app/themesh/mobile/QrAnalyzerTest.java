package app.themesh.mobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.graphics.Rect;
import android.media.Image;

import androidx.camera.core.ImageInfo;
import androidx.camera.core.ImageProxy;

import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import app.themesh.mobile.core.QrImages;

/** Кадры камеры → текст QR-кода: плоскость яркости с выровненными строками, закрытие каждого кадра, углублённый разбор на каждом третьем. */
public class QrAnalyzerTest {
    private static final String TEXT = QrImages.invite(450, 77);

    /** Кадр камеры из готового яркостного буфера: плоскость Y — {@code data}, плоскости U и V пустые. */
    private static final class FakeImage implements ImageProxy {
        private final byte[] y;
        private final int width;
        private final int height;
        private final int rowStride;
        private final int pixelStride;
        private final boolean planesFail;
        int closed;

        FakeImage(byte[] y, int width, int height, int rowStride, int pixelStride, boolean planesFail) {
            this.y = y;
            this.width = width;
            this.height = height;
            this.rowStride = rowStride;
            this.pixelStride = pixelStride;
            this.planesFail = planesFail;
        }

        static FakeImage of(QrImages.Frame f) {
            return new FakeImage(f.luma, f.width, f.height, f.stride, 1, false);
        }

        @Override
        public void close() {
            closed++;
        }

        @Override
        public Rect getCropRect() {
            return null;
        }

        @Override
        public void setCropRect(Rect rect) {
        }

        @Override
        public int getFormat() {
            return 35; // ImageFormat.YUV_420_888
        }

        @Override
        public int getHeight() {
            return height;
        }

        @Override
        public int getWidth() {
            return width;
        }

        @Override
        public PlaneProxy[] getPlanes() {
            if (planesFail) {
                throw new IllegalStateException("кадр уже закрыт");
            }
            return new PlaneProxy[] {plane(y, rowStride, pixelStride), plane(new byte[0], rowStride / 2, 2), plane(new byte[0], rowStride / 2, 2)};
        }

        @Override
        public ImageInfo getImageInfo() {
            return null;
        }

        @Override
        public Image getImage() {
            return null;
        }

        private static PlaneProxy plane(byte[] data, int rowStride, int pixelStride) {
            return new PlaneProxy() {
                @Override
                public int getRowStride() {
                    return rowStride;
                }

                @Override
                public int getPixelStride() {
                    return pixelStride;
                }

                @Override
                public ByteBuffer getBuffer() {
                    ByteBuffer buffer = ByteBuffer.wrap(data);
                    buffer.position(buffer.limit() / 3); // как после чтения: позиция не в начале, анализатор обязан её вернуть
                    return buffer;
                }
            };
        }
    }

    private static final class Sink implements QrAnalyzer.Listener {
        final List<String> texts = new ArrayList<>();
        final List<RuntimeException> failures = new ArrayList<>();

        @Override
        public void onText(String text) {
            texts.add(text);
        }

        @Override
        public void onFailure(RuntimeException e) {
            failures.add(e);
        }
    }

    private static QrImages.Frame frame(String text, int moduleSize, int pad) {
        return QrImages.scene(QrImages.modules(text)).moduleSize(moduleSize).pad(pad).render();
    }

    @Test
    public void aFrameWithRowsLongerThanItsWidthIsRead() {
        Sink sink = new Sink();
        QrAnalyzer analyzer = new QrAnalyzer(sink);
        QrImages.Frame f = frame(TEXT, 4, 64);
        assertTrue(f.stride > f.width);
        FakeImage image = FakeImage.of(f);
        analyzer.analyze(image);
        assertEquals(Arrays.asList(TEXT), sink.texts);
        assertEquals("кадр закрыт", 1, image.closed);
        assertTrue(sink.failures.isEmpty());
    }

    @Test
    public void aBufferThatEndsRightAfterTheLastPixelIsRead() {
        QrImages.Frame f = frame(TEXT, 4, 40);
        int needed = (f.height - 1) * f.stride + f.width;
        Sink sink = new Sink();
        QrAnalyzer analyzer = new QrAnalyzer(sink);
        FakeImage image = new FakeImage(Arrays.copyOf(f.luma, needed), f.width, f.height, f.stride, 1, false);
        analyzer.analyze(image);
        assertEquals(Arrays.asList(TEXT), sink.texts);
        assertEquals(1, image.closed);
    }

    @Test
    public void everyFrameIsClosedWhateverHappensToIt() {
        Sink sink = new Sink();
        QrAnalyzer analyzer = new QrAnalyzer(sink);
        QrImages.Frame good = frame(TEXT, 4, 8);
        QrImages.Frame blank = QrImages.scene(QrImages.modules("x")).moduleSize(1).levels(255, 255).canvas(300, 300).render();
        List<FakeImage> images = new ArrayList<>();
        images.add(FakeImage.of(good)); // читается
        images.add(FakeImage.of(blank)); // пустой
        images.add(new FakeImage(good.luma, good.width, good.height, good.stride, 1, true)); // getPlanes() падает
        images.add(new FakeImage(good.luma, good.width, good.height, good.stride, 2, false)); // не плоскость яркости
        images.add(new FakeImage(good.luma, good.width, good.height, good.width - 1, 1, false)); // строка короче ширины
        images.add(new FakeImage(Arrays.copyOf(good.luma, 100), good.width, good.height, good.stride, 1, false)); // буфер короче кадра
        images.add(new FakeImage(good.luma, 0, 0, 0, 1, false)); // кадр без размера
        for (FakeImage image : images) {
            analyzer.analyze(image);
        }
        for (int i = 0; i < images.size(); i++) {
            assertEquals("кадр " + i + " закрыт ровно один раз", 1, images.get(i).closed);
        }
        assertEquals("прочитан только первый", Arrays.asList(TEXT), sink.texts);
        assertEquals("о падении getPlanes() сказано, остальное — просто не прочитано", 1, sink.failures.size());
    }

    @Test
    public void afterStopFramesAreOnlyClosed() {
        Sink sink = new Sink();
        QrAnalyzer analyzer = new QrAnalyzer(sink);
        analyzer.stop();
        FakeImage image = FakeImage.of(frame(TEXT, 4, 8));
        analyzer.analyze(image);
        assertTrue(sink.texts.isEmpty());
        assertEquals(1, image.closed);
    }

    @Test
    public void theDeepPassRunsOnEveryThirdFrameOnly() {
        // белое на тёмном обычным проходом не читается, а углублённым — да: по тому, на каком кадре текст прочитан, видно, на каком
        // кадре проход углублённый
        Sink sink = new Sink();
        QrAnalyzer analyzer = new QrAnalyzer(sink);
        QrImages.Frame inverted = QrImages.scene(QrImages.modules(TEXT)).moduleSize(4).inverted().pad(5).render();
        List<Integer> readOnFrames = new ArrayList<>();
        for (int i = 1; i <= 9; i++) {
            int before = sink.texts.size();
            FakeImage image = FakeImage.of(inverted);
            analyzer.analyze(image);
            assertEquals(1, image.closed);
            if (sink.texts.size() > before) {
                readOnFrames.add(i);
            }
        }
        assertEquals(Arrays.asList(3, 6, 9), readOnFrames);
        assertEquals(3, QrAnalyzer.DEEP_EVERY);
    }

    @Test
    public void theBufferIsReusedAndFramesOfAnotherSizeFollow() {
        Sink sink = new Sink();
        QrAnalyzer analyzer = new QrAnalyzer(sink);
        String a = QrImages.invite(250, 1);
        String b = QrImages.invite(461, 2);
        analyzer.analyze(FakeImage.of(frame(a, 3, 0)));
        analyzer.analyze(FakeImage.of(frame(b, 6, 33))); // больше первого
        analyzer.analyze(FakeImage.of(frame(a, 2, 5))); // меньше второго
        assertEquals(Arrays.asList(a, b, a), sink.texts);
    }
}
