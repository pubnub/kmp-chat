package com.pubnub.chat.config

/**
 * Represents the severity level of logs that will be printed.
 */
enum class LogLevel {
    /**
     * Turn off logging.
     */
    OFF,

    /**
     * Only print errors.
     */
    ERROR,

    /**
     * Print warnings and errors.
     */
    WARN,

    /**
     * Print warnings, errors and info messages.
     */
    INFO,

    /**
     * Print warnings, errors, info messages and debugging information.
     */
    DEBUG,

    /**
     * The most verbose logging - print all other types of logs and more.
     */
    VERBOSE
}
