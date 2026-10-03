package app.themesh.mobile;

import android.content.Context;

import app.themesh.mobile.core.Texts;

/** Тексты для логики уведомлений и строки состояния — из строковых ресурсов (русские и английские). */
final class AndroidTexts implements Texts {
    private final Context context;

    AndroidTexts(Context context) {
        this.context = context.getApplicationContext();
    }

    @Override
    public String appName() {
        return context.getString(R.string.app_name);
    }

    @Override
    public String offerBody(String file) {
        return context.getString(R.string.notif_offer_body, file);
    }

    @Override
    public String receivedTitle() {
        return context.getString(R.string.notif_received_title);
    }

    @Override
    public String receivedBody(String file, String peer) {
        return context.getString(R.string.notif_received_body, file, peer);
    }

    @Override
    public String copiedTo(String folder) {
        return context.getString(R.string.notif_copied_to, folder);
    }

    @Override
    public String copiedAs(String folder, String name) {
        return context.getString(R.string.notif_copied_as, folder, name);
    }

    @Override
    public String copyNeedsPermission() {
        return context.getString(R.string.notif_copy_no_permission);
    }

    @Override
    public String copyFailed() {
        return context.getString(R.string.notif_copy_failed);
    }

    @Override
    public String chatAttachment() {
        return context.getString(R.string.notif_chat_attachment);
    }

    @Override
    public String mailTitle(String from) {
        return context.getString(R.string.notif_mail_title, from);
    }

    @Override
    public String mailTitleUnknown() {
        return context.getString(R.string.notif_mail_title_unknown);
    }

    @Override
    public String noSubject() {
        return context.getString(R.string.notif_no_subject);
    }

    @Override
    public String statusStarting() {
        return context.getString(R.string.status_starting);
    }

    @Override
    public String statusNoNetwork() {
        return context.getString(R.string.status_no_network);
    }

    @Override
    public String statusAlone() {
        return context.getString(R.string.status_alone);
    }

    @Override
    public String statusOnline(int online, int total) {
        return context.getString(R.string.status_online, online, total);
    }

    @Override
    public String statusStopped() {
        return context.getString(R.string.status_stopped);
    }
}
