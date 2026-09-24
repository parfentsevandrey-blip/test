// Control interface of the :tunnel process. Payloads are JSON (kotlinx.serialization) to avoid
// Parcelable boilerplate; both ends are always the same APK version.
package app.opal.core.tunnel.ipc;

import app.opal.core.tunnel.ipc.ITunnelListener;
import app.opal.core.tunnel.ipc.ITunnelResult;

interface ITunnelService {
    String snapshot();
    void registerListener(ITunnelListener listener);
    void unregisterListener(ITunnelListener listener);
    void disconnect();
    void newIdentity();
    void prewarm(long timeoutMillis);
    void cancelPrewarm();
    void refreshDirectory(long timeoutMillis, ITunnelResult result);
    String diagnostics();
}
