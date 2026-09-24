package app.opal.core.tunnel.ipc;

oneway interface ITunnelListener {
    void onSnapshot(String json);
    void onTraffic(long read, long written, long totalRead, long totalWritten);
}
