package app.themesh.mobile.core;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;

/** Разбор SVG-картинки QR-кода, как её рисует узел (internal/app/qr.go): без него тест с настоящим узлом ничего не докажет. */
public class QrSvgTest {
    @Test
    public void theSvgRoundTripsToTheSameModules() {
        for (String text : new String[] {"MESH1-" + "ABCDEFGH".repeat(20), QrImages.invite(461, 5), "x", "https://example.com/"}) {
            boolean[][] modules = QrImages.modules(text);
            boolean[][] back = QrSvg.parse(QrSvg.toSvg(modules));
            assertEquals(modules.length, back.length);
            for (int y = 0; y < modules.length; y++) {
                assertArrayEquals("строка " + y, modules[y], back[y]);
            }
        }
    }

    @Test
    public void aSvgInTheShapeOfTheNodesIsParsedByHand() {
        // 8×8: поля в 2 модуля вокруг креста из трёх модулей (так проще проверить глазами, чем настоящий QR)
        String svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 7 7\" shape-rendering=\"crispEdges\" role=\"img\" aria-label=\"QR\">"
                + "<rect width=\"7\" height=\"7\" fill=\"#fff\"/><path d=\"M3 2h1v1h-1zM2 3h3v1h-3zM3 4h1v1h-1z\" fill=\"#000\"/></svg>";
        boolean[][] m = QrSvg.parse(svg);
        assertEquals(7, m.length);
        String[] rows = new String[7];
        for (int y = 0; y < 7; y++) {
            StringBuilder sb = new StringBuilder();
            for (int x = 0; x < 7; x++) {
                sb.append(m[y][x] ? '#' : '.');
            }
            rows[y] = sb.toString();
        }
        assertEquals(".......", rows[0]);
        assertEquals(".......", rows[1]);
        assertEquals("...#...", rows[2]);
        assertEquals("..###..", rows[3]);
        assertEquals("...#...", rows[4]);
        assertEquals(".......", rows[5]);
        assertEquals(".......", rows[6]);
    }

    @Test
    public void attributesInAnotherOrderAreFine() {
        String svg = "<svg viewBox=\"0 0 5 5\"><path fill=\"#000\" d=\"M1 1h3v1h-3z\"/></svg>";
        boolean[][] m = QrSvg.parse(svg);
        assertEquals(true, m[1][1] && m[1][2] && m[1][3]);
        assertEquals(false, m[1][0] || m[1][4] || m[0][1] || m[2][1]);
    }

    @Test
    public void aSvgThatIsNotWhatWeExpectIsRefusedLoudly() {
        String[] bad = {
                "",
                "<svg viewBox=\"0 0 5 6\"><path d=\"M0 0h1v1h-1z\" fill=\"#000\"/></svg>",
                "<svg viewBox=\"0 0 5 5\"><rect width=\"5\" height=\"5\" fill=\"#fff\"/></svg>",
                "<svg viewBox=\"0 0 5 5\"><path d=\"M0 0h1v1h-1z\" fill=\"#fff\"/></svg>",
                "<svg viewBox=\"0 0 5 5\"><path d=\"M0 0l1 1z\" fill=\"#000\"/></svg>",
                "<svg viewBox=\"0 0 5 5\"><path d=\"M0 0h2v1h-1z\" fill=\"#000\"/></svg>",
                "<svg viewBox=\"0 0 5 5\"><path d=\"M4 0h2v1h-2z\" fill=\"#000\"/></svg>",
                "<svg viewBox=\"0 0 5 5\"><path d=\"M0 9h1v1h-1z\" fill=\"#000\"/></svg>",
                "<svg viewBox=\"0 0 5 5\"><path d=\"M0 0h1v1h-1zZ\" fill=\"#000\"/></svg>"};
        for (String svg : bad) {
            try {
                QrSvg.parse(svg);
                fail("должно было не разобраться: " + svg);
            } catch (IllegalArgumentException expected) {
                // понятное сообщение вместо молча неверной картинки
                assertEquals(false, expected.getMessage() == null || expected.getMessage().isEmpty());
            }
        }
    }
}
