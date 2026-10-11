package io.github.matejkadlec.realmforge.qa.castanim;

/** Handles {@link QaCastPayload} on the client; implemented in {@code qa/client/castanim}, so this side stays common. */
@FunctionalInterface
public interface QaCastClientHandler {
    void handle(QaCastPayload payload);
}
