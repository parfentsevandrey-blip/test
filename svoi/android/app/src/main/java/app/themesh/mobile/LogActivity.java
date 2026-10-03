package app.themesh.mobile;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.activity.EdgeToEdge;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import app.themesh.mobile.core.AppLog;

/**
 * «Журнал»: хвост файла themesh.log — вывод узла и короткие записи приложения. Его можно скопировать или
 * отправить (текстом): для разбора ошибок. Секретов в журнале нет (см. {@link AppLog}).
 */
public class LogActivity extends ComponentActivity {
    /** Сколько читать с конца файла: хватает на последние сутки обычной работы. */
    private static final int TAIL_BYTES = 200 * 1024;
    /** Сколько отправлять другому приложению: Intent не любит мегабайты. */
    private static final int SHARE_BYTES = 100 * 1024;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> new Thread(r, "themesh-log-io"));
    private TextView text;
    private ScrollView scroll;
    private String shown = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_log);
        SystemBars.fit(findViewById(R.id.log_root));
        text = findViewById(R.id.log_text);
        scroll = findViewById(R.id.log_scroll);
        findViewById(R.id.log_copy).setOnClickListener(v -> copy());
        findViewById(R.id.log_share).setOnClickListener(v -> share());
        findViewById(R.id.log_refresh).setOnClickListener(v -> load());
        load();
    }

    @Override
    protected void onDestroy() {
        io.shutdownNow();
        main.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private File logFile() {
        return new File(getFilesDir(), "themesh.log");
    }

    private void load() {
        io.execute(() -> {
            String content;
            boolean truncated = logFile().length() > TAIL_BYTES;
            try {
                content = new String(AppLog.readTail(logFile(), TAIL_BYTES), StandardCharsets.UTF_8);
            } catch (IOException e) {
                content = String.valueOf(e);
                truncated = false;
            }
            // Хвост начался посреди строки (и, возможно, символа): первую неполную строку не показываем.
            int firstNewline = content.indexOf('\n');
            if (truncated && firstNewline >= 0) {
                content = content.substring(firstNewline + 1);
            }
            String result = content;
            main.post(() -> {
                if (isDestroyed()) {
                    return;
                }
                shown = result;
                text.setText(result.isEmpty() ? getString(R.string.log_empty) : result);
                scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
            });
        });
    }

    private void copy() {
        ClipboardManager cm = getSystemService(ClipboardManager.class);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText(getString(R.string.log_title), tail(TAIL_BYTES)));
            Toast.makeText(this, R.string.log_copied, Toast.LENGTH_SHORT).show();
        }
    }

    private void share() {
        Intent send = new Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, getString(R.string.log_share_title))
                .putExtra(Intent.EXTRA_TEXT, tail(SHARE_BYTES));
        startActivity(Intent.createChooser(send, getString(R.string.log_share_title)));
    }

    /** Последние {@code max} символов показанного журнала (по границе строки, если можно). */
    private String tail(int max) {
        if (shown.length() <= max) {
            return shown;
        }
        String s = shown.substring(shown.length() - max);
        int nl = s.indexOf('\n');
        return nl >= 0 ? s.substring(nl + 1) : s;
    }
}
