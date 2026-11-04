package io.demo.companion;

/** All callbacks on the UI thread. Implementations own network/auth/session details. */
public interface SharingTransport {
    interface Listener { void onChanged(SharingModels.Snapshot snapshot); }
    void setListener(Listener listener);
    SharingModels.Snapshot snapshot();
    void createGroup();
    void joinGroup(String inviteCode);
    /** Latest local sensor state; null withdraws unavailable location. Never queues historical samples. */
    default void publish(SharingModels.Vehicle own) { }
    void startSharing();
    void stopSharing();
    void leaveGroup();
    void send(SharingModels.MessageKind kind);
    void acknowledge(String messageId);
    void close();
}
