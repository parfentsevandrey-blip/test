package app.themesh.mobile.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeNoException;
import static org.junit.Assume.assumeTrue;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** Копии полученных файлов в «Загрузки/The Mesh»: когда делаются, когда нет и что говорится в уведомлении. */
public class ReceivedFilesTest {
    /** Подделка вместо MediaStore: помнит, какие имена в «Загрузках» заняты, и что в неё положили. */
    private static final class FakeStore implements ReceivedFiles.Store {
        boolean canWrite = true;
        IOException failWith;
        final Set<String> existing = new TreeSet<>();
        final List<String> copies = new ArrayList<>(); // «имя|тип|содержимое»

        @Override
        public boolean canWrite() {
            return canWrite;
        }

        @Override
        public String copy(File source, String name, String mime) throws IOException {
            if (failWith != null) {
                throw failWith;
            }
            String unique = FileNames.uniqueName(name, existing::contains);
            existing.add(unique);
            copies.add(unique + "|" + mime + "|" + new String(Files.readAllBytes(source.toPath()), StandardCharsets.UTF_8));
            return unique;
        }
    }

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private File appFiles;
    private File nodeData;
    private File downloads; // <filesDir>/Downloads/The Mesh: сюда узел кладёт принятое
    private FakeStore store;
    private boolean enabled;
    private ReceivedFiles files;

    @Before
    public void setUp() throws Exception {
        appFiles = tmp.newFolder("files");
        nodeData = new File(appFiles, "themesh");
        downloads = new File(appFiles, "Downloads/The Mesh");
        assertTrue(nodeData.mkdirs() && downloads.mkdirs());
        store = new FakeStore();
        enabled = true;
        files = new ReceivedFiles(store, () -> enabled, appFiles, nodeData);
    }

    private File received(String name, String content) throws IOException {
        File f = new File(downloads, name);
        Files.write(f.toPath(), content.getBytes(StandardCharsets.UTF_8));
        return f;
    }

    /** Событие {@code transfer} как его шлёт узел (docs/UI-API.md). */
    private static JSONObject transfer(String id, String dir, String state, String path, String mime) throws JSONException {
        return new JSONObject().put("id", id).put("dir", dir).put("state", state).put("peer", "p1").put("peerName", "Pixel")
                .put("name", "photo.jpg").put("size", 4).put("mime", mime).put("path", path);
    }

    private static JSONObject finished(String id, File file) throws JSONException {
        return transfer(id, "in", "done", file.getPath(), "image/jpeg");
    }

    // ---- когда копируем -----------------------------------------------------------------------

    @Test
    public void aFinishedIncomingTransferIsCopiedAndThePrivateFileStays() throws Exception {
        File f = received("photo.jpg", "jpeg-bytes");
        ReceivedFiles.Result r = files.onEvent("transfer", finished("t1", f));
        assertNotNull(r);
        assertEquals(ReceivedFiles.Outcome.COPIED, r.outcome);
        assertEquals("photo.jpg", r.savedName);
        assertFalse(r.renamed());
        assertEquals(1, store.copies.size());
        assertEquals("photo.jpg|image/jpeg|jpeg-bytes", store.copies.get(0));
        assertTrue("своя копия остаётся: на неё ссылается интерфейс", f.isFile());
    }

    @Test
    public void outgoingTransfersAreNeverCopied() throws Exception {
        File f = received("photo.jpg", "x");
        for (String state : new String[] {"done", "offered", "queued", "active"}) {
            assertNull(state, files.onEvent("transfer", transfer("o-" + state, "out", state, f.getPath(), "image/jpeg")));
        }
        assertTrue(store.copies.isEmpty());
    }

    @Test
    public void onlyAFinishedTransferIsCopied() throws Exception {
        File f = received("photo.jpg", "x");
        for (String state : new String[] {"offered", "queued", "active", "failed", "declined", "canceled", ""}) {
            assertNull(state, files.onEvent("transfer", transfer("t-" + state, "in", state, f.getPath(), "image/jpeg")));
        }
        assertTrue(store.copies.isEmpty());
    }

    @Test
    public void otherKindsOfEventsAreIgnored() throws Exception {
        File f = received("photo.jpg", "x");
        assertNull(files.onEvent("chat", finished("c1", f)));
        assertNull(files.onEvent("mail", finished("m1", f)));
        assertNull(files.onEvent("transfer.removed", finished("t9", f)));
        assertNull(files.onEvent("transfer", null));
        assertTrue(store.copies.isEmpty());
    }

    @Test
    public void isFinishedIncomingIsPrecise() throws Exception {
        File f = received("photo.jpg", "x");
        assertTrue(ReceivedFiles.isFinishedIncoming("transfer", finished("t1", f)));
        assertFalse(ReceivedFiles.isFinishedIncoming("transfer", transfer("t2", "out", "done", f.getPath(), "")));
        assertFalse(ReceivedFiles.isFinishedIncoming("transfer", transfer("t3", "in", "active", f.getPath(), "")));
        assertFalse(ReceivedFiles.isFinishedIncoming("chat", finished("t4", f)));
        assertFalse(ReceivedFiles.isFinishedIncoming("transfer", null));
    }

    @Test
    public void aTransferIsHandledOnlyOnceHoweverOftenTheEventComes() throws Exception {
        File f = received("photo.jpg", "x");
        assertEquals(ReceivedFiles.Outcome.COPIED, files.onEvent("transfer", finished("t1", f)).outcome);
        assertNull(files.onEvent("transfer", finished("t1", f)));
        assertNull(files.onEvent("transfer", finished("t1", f)));
        assertEquals(1, store.copies.size());
    }

    // ---- настройка ----------------------------------------------------------------------------

    @Test
    public void withTheSettingOffNothingIsCopiedAndTheNotificationStaysAsItWas() throws Exception {
        enabled = false;
        File f = received("photo.jpg", "x");
        ReceivedFiles.Result r = files.onEvent("transfer", finished("t1", f));
        assertEquals(ReceivedFiles.Outcome.OFF, r.outcome);
        assertTrue(store.copies.isEmpty());
        assertEquals("", ReceivedFiles.describe(r, ResourceTexts.ru()));
        assertEquals("", ReceivedFiles.describe(r, ResourceTexts.en()));
    }

    @Test
    public void theSettingIsReadForEveryFile() throws Exception {
        enabled = false;
        assertEquals(ReceivedFiles.Outcome.OFF, files.onEvent("transfer", finished("t1", received("a.txt", "a"))).outcome);
        enabled = true;
        assertEquals(ReceivedFiles.Outcome.COPIED, files.onEvent("transfer", finished("t2", received("b.txt", "b"))).outcome);
        enabled = false;
        assertEquals(ReceivedFiles.Outcome.OFF, files.onEvent("transfer", finished("t3", received("c.txt", "c"))).outcome);
        assertEquals(1, store.copies.size());
        assertTrue(store.copies.get(0).startsWith("b.txt|"));
    }

    @Test
    public void aFileReceivedWhileTheSettingWasOffIsNotCopiedLaterWhenTheEventRepeats() throws Exception {
        enabled = false;
        File f = received("photo.jpg", "x");
        assertEquals(ReceivedFiles.Outcome.OFF, files.onEvent("transfer", finished("t1", f)).outcome);
        enabled = true;
        assertNull(files.onEvent("transfer", finished("t1", f)));
        assertTrue(store.copies.isEmpty());
    }

    // ---- одинаковые имена ---------------------------------------------------------------------

    @Test
    public void aNameThatIsTakenInDownloadsGetsTheNextFreeOne() throws Exception {
        store.existing.add("photo.jpg"); // от прежней копии или чужой файл
        ReceivedFiles.Result r = files.onEvent("transfer", finished("t1", received("photo.jpg", "new")));
        assertEquals(ReceivedFiles.Outcome.COPIED, r.outcome);
        assertEquals("photo.jpg", r.name);
        assertEquals("photo (2).jpg", r.savedName);
        assertTrue(r.renamed());
        assertEquals("photo (2).jpg|image/jpeg|new", store.copies.get(0));
        assertTrue("прежний файл не тронут", store.existing.contains("photo.jpg"));
    }

    @Test
    public void theSameNameReceivedTwiceGivesTwoCopies() throws Exception {
        File first = received("photo.jpg", "one");
        assertEquals("photo.jpg", files.onEvent("transfer", finished("t1", first)).savedName);
        assertTrue(first.delete()); // человек убрал файл из папки приложения, а потом тот же пришёл снова
        File again = received("photo.jpg", "two");
        ReceivedFiles.Result r = files.onEvent("transfer", finished("t2", again));
        assertEquals("photo (2).jpg", r.savedName);
        assertEquals(2, store.copies.size());
        assertEquals("photo.jpg|image/jpeg|one", store.copies.get(0));
        assertEquals("photo (2).jpg|image/jpeg|two", store.copies.get(1));
    }

    @Test
    public void theNodesOwnNumberedNameIsKept() throws Exception {
        // узел сам нумерует повторы в своей папке: «photo (1).jpg»
        File f = received("photo (1).jpg", "x");
        assertEquals("photo (1).jpg", files.onEvent("transfer", finished("t1", f)).savedName);
    }

    @Test
    public void theNameIsMadeSafeForTheDownloadsFolder() throws Exception {
        File f = received("weird:name?.txt", "x"); // на Linux такое имя допустимо, на FAT и в MediaStore — нет
        ReceivedFiles.Result r = files.onEvent("transfer", finished("t1", f));
        assertEquals("weird_name_.txt", r.savedName);
    }

    // ---- нет разрешения, ошибки ---------------------------------------------------------------

    @Test
    public void withoutPermissionOnOldAndroidNothingIsCopiedAndTheNotificationSaysSo() throws Exception {
        store.canWrite = false;
        ReceivedFiles.Result r = files.onEvent("transfer", finished("t1", received("photo.jpg", "x")));
        assertEquals(ReceivedFiles.Outcome.NO_PERMISSION, r.outcome);
        assertTrue(store.copies.isEmpty());
        assertEquals("Копия в «Загрузки» не сделана: нет разрешения на запись в память", ReceivedFiles.describe(r, ResourceTexts.ru()));
        assertEquals("No copy in Downloads: the app may not write to storage", ReceivedFiles.describe(r, ResourceTexts.en()));

        store.canWrite = true; // разрешение выдали: следующие файлы копируются
        assertEquals(ReceivedFiles.Outcome.COPIED, files.onEvent("transfer", finished("t2", received("b.jpg", "b"))).outcome);
    }

    @Test
    public void theSettingOffWinsOverAMissingPermission() throws Exception {
        enabled = false;
        store.canWrite = false;
        ReceivedFiles.Result r = files.onEvent("transfer", finished("t1", received("photo.jpg", "x")));
        assertEquals(ReceivedFiles.Outcome.OFF, r.outcome);
        assertEquals("", ReceivedFiles.describe(r, ResourceTexts.ru()));
    }

    @Test
    public void aCopyThatFailsIsReportedNotThrownAndLaterFilesStillWork() throws Exception {
        store.failWith = new IOException("disk full: /data/user/0/app.themesh.mobile/files/Downloads/The Mesh/secret-plan.pdf");
        ReceivedFiles.Result r = files.onEvent("transfer", finished("t1", received("secret-plan.pdf", "x")));
        assertEquals(ReceivedFiles.Outcome.FAILED, r.outcome);
        assertEquals("Копию в «Загрузки» сделать не удалось", ReceivedFiles.describe(r, ResourceTexts.ru()));
        assertEquals("Could not copy it to Downloads", ReceivedFiles.describe(r, ResourceTexts.en()));
        assertFalse("в журнал не попадают ни имена файлов, ни пути", r.error.contains("secret") || r.error.contains("/"));
        assertEquals("IOException", r.error);

        store.failWith = null;
        assertEquals(ReceivedFiles.Outcome.COPIED, files.onEvent("transfer", finished("t2", received("b.pdf", "b"))).outcome);
    }

    @Test
    public void aRuntimeFailureInTheStoreIsAlsoJustAFailure() throws Exception {
        ReceivedFiles broken = new ReceivedFiles(new ReceivedFiles.Store() {
            @Override
            public boolean canWrite() {
                return true;
            }

            @Override
            public String copy(File source, String name, String mime) {
                throw new IllegalStateException("MediaStore is gone");
            }
        }, () -> true, appFiles, nodeData);
        assertEquals(ReceivedFiles.Outcome.FAILED, broken.onEvent("transfer", finished("t1", received("a.txt", "a"))).outcome);
    }

    // ---- что можно копировать -----------------------------------------------------------------

    @Test
    public void aMissingFileIsSkippedQuietly() throws Exception {
        ReceivedFiles.Result r = files.onEvent("transfer", finished("t1", new File(downloads, "gone.jpg")));
        assertEquals(ReceivedFiles.Outcome.SKIPPED, r.outcome);
        assertEquals("", ReceivedFiles.describe(r, ResourceTexts.ru()));
        assertTrue(store.copies.isEmpty());
    }

    @Test
    public void aFileWithoutAPathIsSkipped() throws Exception {
        assertEquals(ReceivedFiles.Outcome.SKIPPED, files.onEvent("transfer", transfer("t1", "in", "done", "", "")).outcome);
        JSONObject noPath = new JSONObject().put("id", "t2").put("dir", "in").put("state", "done").put("name", "a.txt");
        assertEquals(ReceivedFiles.Outcome.SKIPPED, files.onEvent("transfer", noPath).outcome);
        assertEquals(ReceivedFiles.Outcome.SKIPPED, files.onEvent("transfer", transfer("t3", "in", "done", "photo.jpg", "")).outcome); // не абсолютный
        assertTrue(store.copies.isEmpty());
    }

    @Test
    public void anEventWithoutAnIdIsIgnored() throws Exception {
        File f = received("photo.jpg", "x");
        assertNull(files.onEvent("transfer", transfer("", "in", "done", f.getPath(), "")));
        assertTrue(store.copies.isEmpty());
    }

    @Test
    public void aDirectoryIsNotCopied() throws Exception {
        assertEquals(ReceivedFiles.Outcome.SKIPPED, files.onEvent("transfer", finished("t1", downloads)).outcome);
        assertTrue(store.copies.isEmpty());
    }

    @Test
    public void aFileOutsideTheAppsFilesIsNotCopied() throws Exception {
        // человек выбрал в настройках общую папку: файл уже на виду, второй раз его класть некуда и незачем
        File elsewhere = tmp.newFile("shared-photo.jpg");
        assertEquals(ReceivedFiles.Outcome.SKIPPED, files.onEvent("transfer", finished("t1", elsewhere)).outcome);
        assertTrue(store.copies.isEmpty());
    }

    @Test
    public void theNodesOwnDataIsNeverCopied() throws Exception {
        File key = new File(nodeData, "device.key");
        Files.write(key.toPath(), "secret".getBytes(StandardCharsets.UTF_8));
        assertEquals(ReceivedFiles.Outcome.SKIPPED, files.onEvent("transfer", finished("t1", key)).outcome);
        File deeper = new File(nodeData, "blobs/ab/cd");
        assertTrue(deeper.getParentFile().mkdirs());
        Files.write(deeper.toPath(), "blob".getBytes(StandardCharsets.UTF_8));
        assertEquals(ReceivedFiles.Outcome.SKIPPED, files.onEvent("transfer", finished("t2", deeper)).outcome);
        assertTrue(store.copies.isEmpty());
    }

    @Test
    public void aPathThatClimbsOutOfTheAppsFilesIsNotCopied() throws Exception {
        File outside = tmp.newFile("outside.txt");
        String sneaky = downloads.getPath() + "/../../../" + outside.getName();
        assertTrue(new File(sneaky).isFile()); // путь «настоящий», но ведёт за пределы папки приложения
        assertEquals(ReceivedFiles.Outcome.SKIPPED, files.onEvent("transfer", transfer("t1", "in", "done", sneaky, "")).outcome);
        // а так же ведущий внутрь данных узла
        File key = new File(nodeData, "device.key");
        Files.write(key.toPath(), "secret".getBytes(StandardCharsets.UTF_8));
        String intoData = downloads.getPath() + "/../../themesh/device.key";
        assertEquals(ReceivedFiles.Outcome.SKIPPED, files.onEvent("transfer", transfer("t2", "in", "done", intoData, "")).outcome);
        assertTrue(store.copies.isEmpty());
    }

    @Test
    public void aSymlinkInTheDownloadFolderThatPointsOutsideIsNotFollowed() throws Exception {
        File outside = tmp.newFile("private-elsewhere.txt");
        File link = new File(downloads, "innocent.txt");
        try {
            Files.createSymbolicLink(link.toPath(), outside.toPath());
        } catch (UnsupportedOperationException | IOException e) {
            assumeNoException("символические ссылки недоступны", e);
        }
        assertEquals(ReceivedFiles.Outcome.SKIPPED, files.onEvent("transfer", finished("t1", link)).outcome);
        assertTrue(store.copies.isEmpty());
    }

    @Test
    public void aFolderThatOnlyStartsWithTheSameNameIsNotInsideTheAppsFiles() throws Exception {
        File sibling = new File(appFiles.getParentFile(), appFiles.getName() + "-other");
        assertTrue(sibling.mkdirs());
        File f = new File(sibling, "a.txt");
        Files.write(f.toPath(), "x".getBytes(StandardCharsets.UTF_8));
        assertEquals(ReceivedFiles.Outcome.SKIPPED, files.onEvent("transfer", finished("t1", f)).outcome);
    }

    @Test
    public void foldersInsideTheDownloadFolderAreFine() throws Exception {
        // узел может положить файл в подпапку (если когда-нибудь научится): это всё ещё папка приложения
        File sub = new File(downloads, "album");
        assertTrue(sub.mkdirs());
        File f = new File(sub, "a.txt");
        Files.write(f.toPath(), "x".getBytes(StandardCharsets.UTF_8));
        ReceivedFiles.Result r = files.onEvent("transfer", finished("t1", f));
        assertEquals(ReceivedFiles.Outcome.COPIED, r.outcome);
        assertEquals("a.txt", r.savedName);
    }

    // ---- что видит человек --------------------------------------------------------------------

    private static final Notices.Lookup NO_LOOKUP = new Notices.Lookup() {
        @Override
        public String peerName(String peerId) {
            return "";
        }

        @Override
        public JSONObject mail(String id) throws IOException {
            throw new IOException("no mail here");
        }
    };

    @Test
    public void theNotificationSaysWhereTheFileWent() throws Exception {
        File f = received("photo.jpg", "x");
        JSONObject event = finished("t1", f);
        Notice notice = Notices.forEvent("transfer", event, ResourceTexts.ru(), NO_LOOKUP);
        assertNotNull(notice);
        assertEquals(Notice.Kind.RECEIVED, notice.kind);
        String line = ReceivedFiles.describe(files.onEvent("transfer", event), ResourceTexts.ru());
        assertEquals("Копия — в «Загрузки/The Mesh»", line);
        Notice shown = notice.withBody(notice.body + "\n" + line);
        assertEquals("«photo.jpg» — от устройства Pixel\nКопия — в «Загрузки/The Mesh»", shown.body);
        assertEquals("заголовок, ключ и переход прежние", notice.title, shown.title);
        assertEquals(notice.key, shown.key);
        assertEquals(notice.tag, shown.tag);
        assertEquals(notice.route, shown.route);
    }

    @Test
    public void theEnglishNotificationSaysItToo() throws Exception {
        JSONObject event = finished("t1", received("photo.jpg", "x"));
        Notice notice = Notices.forEvent("transfer", event, ResourceTexts.en(), NO_LOOKUP);
        String line = ReceivedFiles.describe(files.onEvent("transfer", event), ResourceTexts.en());
        assertEquals("A copy is in Downloads/The Mesh", line);
        assertEquals("“photo.jpg” — from Pixel\nA copy is in Downloads/The Mesh", notice.withBody(notice.body + "\n" + line).body);
    }

    @Test
    public void aRenamedCopyIsNamedInTheNotification() throws Exception {
        store.existing.add("photo.jpg");
        ReceivedFiles.Result r = files.onEvent("transfer", finished("t1", received("photo.jpg", "x")));
        assertEquals("Копия — в «Загрузки/The Mesh» под именем «photo (2).jpg»", ReceivedFiles.describe(r, ResourceTexts.ru()));
        assertEquals("A copy is in Downloads/The Mesh as “photo (2).jpg”", ReceivedFiles.describe(r, ResourceTexts.en()));
    }

    @Test
    public void nothingToSayWhenThereWasNoEvent() throws Exception {
        assertEquals("", ReceivedFiles.describe(null, ResourceTexts.ru()));
    }

    @Test
    public void russianNamesAndEmojiSurviveTheTrip() throws Exception {
        // имена файлов с кириллицей и эмодзи JVM на компьютере создаёт, только если системная кодировка — UTF-8
        assumeTrue("нужна системная кодировка UTF-8", "UTF-8".equalsIgnoreCase(System.getProperty("sun.jnu.encoding", "")));
        File f = received("Отчёт 📱 за май.pdf", "x");
        ReceivedFiles.Result r = files.onEvent("transfer", finished("t1", f));
        assertEquals("Отчёт 📱 за май.pdf", r.savedName);
        assertEquals("Отчёт 📱 за май.pdf|image/jpeg|x", store.copies.get(0));
    }
}
