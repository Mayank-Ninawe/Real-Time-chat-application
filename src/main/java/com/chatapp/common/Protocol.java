package com.chatapp.common;

/**
 * Common protocol definitions and message utility functions for the TCP Chat Application.
 */
public final class Protocol {

    private Protocol() {
        // Prevent instantiation
    }

    public static final String COMMAND_QUIT = "/quit";
    public static final String SYSTEM_PREFIX = "*** ";
    public static final String SYSTEM_SUFFIX = " ***";

    /**
     * Formats a user message for broadcast.
     * Output format: [username]: message
     */
    public static String formatUserMessage(String username, String message) {
        return "[" + username + "]: " + message;
    }

    /**
     * Formats a system message for broadcast (joins, leaves, announcements).
     * Output format: *** message ***
     */
    public static String formatSystemMessage(String message) {
        return SYSTEM_PREFIX + message + SYSTEM_SUFFIX;
    }
}
