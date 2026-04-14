package com.wannaverse.chimesdk

/** Lifecycle state of the active meeting session. */
enum class ConnectionStatus {
    /** Initial handshake in progress. */
    CONNECTING,
    /** Session is active and healthy. */
    CONNECTED,
    /** Connection was lost; SDK is attempting to recover. */
    RECONNECTING,
    /** Session is active but network quality is degraded. */
    POOR_CONNECTION,
    /** Session ended cleanly. */
    DISCONNECTED,
    /** Session ended due to an unrecoverable error. */
    ERROR
}
