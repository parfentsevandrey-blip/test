package app.themesh.mobile.core;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * QR-код в виде SVG, как его отдаёт узел ({@code qrSvg} в {@code POST /api/invites}, internal/app/qr.go):
 * {@code <svg viewBox="0 0 N N"><rect width="N" height="N" fill="#fff"/><path d="M x yh<run>v1h-<run>z …" fill="#000"/></svg>} —
 * чёрные модули нарисованы прямоугольниками-«пробегами» по строкам, поля в 4 модуля уже внутри квадрата N×N. Здесь SVG
 * разбирается обратно в матрицу модулей (и собирается из неё — чтобы проверить сам разбор, не дожидаясь узла).
 */
final class QrSvg {
    private static final Pattern VIEW_BOX = Pattern.compile("viewBox=\"0 0 (\\d+) (\\d+)\"");
    private static final Pattern PATH = Pattern.compile("<path\\b([^>]*)>");
    private static final Pattern ATTR_D = Pattern.compile("\\bd=\"([^\"]*)\"");
    private static final Pattern ATTR_BLACK_FILL = Pattern.compile("\\bfill=\"(?:#000|#000000|black)\"");
    private static final Pattern RUN = Pattern.compile("M(\\d+) (\\d+)h(\\d+)v1h-(\\d+)z");

    private QrSvg() {
    }

    /** Матрица модулей ({@code [строка][столбец]}, {@code true} — чёрный); если SVG не такой, как ждём, — IllegalArgumentException. */
    static boolean[][] parse(String svg) {
        Matcher box = VIEW_BOX.matcher(svg);
        if (!box.find() || !box.group(1).equals(box.group(2))) {
            throw new IllegalArgumentException("нет квадратного viewBox: " + abbreviate(svg));
        }
        int n = Integer.parseInt(box.group(1));
        String d = null;
        Matcher path = PATH.matcher(svg);
        while (d == null && path.find()) {
            Matcher fill = ATTR_BLACK_FILL.matcher(path.group(1));
            Matcher data = ATTR_D.matcher(path.group(1));
            if (fill.find() && data.find()) {
                d = data.group(1);
            }
        }
        if (d == null) {
            throw new IllegalArgumentException("нет чёрного <path d=\"…\">: " + abbreviate(svg));
        }
        boolean[][] modules = new boolean[n][n];
        Matcher run = RUN.matcher(d);
        int end = 0;
        while (run.find()) {
            if (run.start() != end) {
                throw new IllegalArgumentException("в пути лишнее: «" + d.substring(end, run.start()) + "»");
            }
            end = run.end();
            int x = Integer.parseInt(run.group(1));
            int y = Integer.parseInt(run.group(2));
            int length = Integer.parseInt(run.group(3));
            if (length != Integer.parseInt(run.group(4)) || length < 1 || y >= n || x + length > n) {
                throw new IllegalArgumentException("неверный пробег: " + run.group());
            }
            for (int i = 0; i < length; i++) {
                modules[y][x + i] = true;
            }
        }
        if (end != d.length()) {
            throw new IllegalArgumentException("в конце пути лишнее: «" + d.substring(end) + "»");
        }
        return modules;
    }

    /** SVG в том же виде, что у узла (internal/app/qr.go, qrSVG): по матрице модулей вместе с полями. */
    static String toSvg(boolean[][] modules) {
        int n = modules.length;
        StringBuilder path = new StringBuilder();
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; ) {
                if (!modules[y][x]) {
                    x++;
                    continue;
                }
                int run = 1;
                while (x + run < n && modules[y][x + run]) {
                    run++;
                }
                path.append('M').append(x).append(' ').append(y).append('h').append(run).append("v1h-").append(run).append('z');
                x += run;
            }
        }
        return "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 " + n + " " + n + "\" shape-rendering=\"crispEdges\" role=\"img\" aria-label=\"QR\">"
                + "<rect width=\"" + n + "\" height=\"" + n + "\" fill=\"#fff\"/><path d=\"" + path + "\" fill=\"#000\"/></svg>";
    }

    private static String abbreviate(String s) {
        return s.length() <= 200 ? s : s.substring(0, 200) + "…";
    }
}
