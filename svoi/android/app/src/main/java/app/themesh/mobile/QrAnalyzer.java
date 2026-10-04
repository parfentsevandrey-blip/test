package app.themesh.mobile;

import androidx.annotation.NonNull;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;

import java.nio.ByteBuffer;

import app.themesh.mobile.core.QrDecoder;

/**
 * Кадры камеры → текст QR-кода: берёт плоскость яркости (Y) кадра YUV_420_888 и отдаёт её {@link QrDecoder}. Работает в
 * потоке анализа CameraX, по одному кадру за раз (остальные CameraX отбрасывает: STRATEGY_KEEP_ONLY_LATEST). Кадр
 * закрывается всегда, в {@code finally}: пока он не закрыт, камера новых не даёт. Ничего не сохраняется и не передаётся:
 * кадр копируется в буфер, который живёт до следующего кадра.
 */
final class QrAnalyzer implements ImageAnalysis.Analyzer {
    /** Кто получает результат. Вызывается из потока анализа. */
    interface Listener {
        /** На кадре прочитан QR-код (любой: приглашение это или нет, решает вызывающий). */
        void onText(String text);

        /** Кадр разобрать не удалось (не должно случаться); сканирование продолжается. */
        void onFailure(RuntimeException e);
    }

    /** Углублённый разбор (общий порог, инвертированный кадр) — на каждом третьем кадре: так нагрузка на кадр невелика. */
    static final int DEEP_EVERY = 3;
    /** Кадр больше этого (64 МБ яркости, около 8000×8000) не бывает; такой не разбираем, чтобы не выделять память зря. */
    private static final long MAX_FRAME_BYTES = 64L << 20;

    private final Listener listener;
    private byte[] luma = new byte[0];
    private volatile int frames; // пишет один поток (анализа), читают тесты на устройстве
    private volatile boolean stopped;

    QrAnalyzer(Listener listener) {
        this.listener = listener;
    }

    /** Сколько кадров дошло до распознавания (годных по размеру и строке): по нему видно, что камера отдаёт кадры анализатору. */
    int frames() {
        return frames;
    }

    /** Больше кадров не разбирать (результат уже получен или экран закрывается): они только закрываются. */
    void stop() {
        stopped = true;
    }

    @Override
    public void analyze(@NonNull ImageProxy image) {
        try {
            if (stopped) {
                return;
            }
            String text = decode(image);
            if (text != null && !stopped) {
                listener.onText(text);
            }
        } catch (RuntimeException e) {
            listener.onFailure(e);
        } finally {
            image.close();
        }
    }

    private String decode(ImageProxy image) {
        ImageProxy.PlaneProxy[] planes = image.getPlanes();
        if (planes == null || planes.length == 0) {
            return null;
        }
        ImageProxy.PlaneProxy y = planes[0];
        int width = image.getWidth();
        int height = image.getHeight();
        int rowStride = y.getRowStride(); // не равен ширине: строки выровнены, в хвосте строки — мусор
        if (width <= 0 || height <= 0 || y.getPixelStride() != 1 || rowStride < width) {
            return null;
        }
        // Нужны height - 1 полных строк и в последней только width байт: у части камер буфер кончается ровно на них.
        long needed = (long) (height - 1) * rowStride + width;
        ByteBuffer buffer = y.getBuffer().duplicate();
        buffer.rewind();
        if (needed > MAX_FRAME_BYTES || buffer.remaining() < needed) {
            return null;
        }
        int total = rowStride * height;
        if (luma.length < total) {
            luma = new byte[total];
        }
        buffer.get(luma, 0, Math.min(buffer.remaining(), total));
        frames++;
        return QrDecoder.decode(luma, rowStride, width, height, frames % DEEP_EVERY == 0);
    }
}
