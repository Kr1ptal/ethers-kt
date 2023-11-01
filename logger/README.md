# logger

Implements zero-cost extension functions for logging at various levels, without having to manually check if the log
level is enabled. This avoids creating potentially expensive log messages if the log level is not enabled.

When implementing logging within a class, utilize the `getLogger()` function to automatically obtain the correct logger.

## 💻 Code Examples

- Log messages at different log levels.

    ```kotlin
    val LOG = getLogger()
  
    LOG.trc { "trace message" }
    LOG.dbg { "debug message" }
    LOG.inf { "info message" }
    LOG.wrn { "warn message" }
    LOG.err { "error message" }
    LOG.err(Exception("thrown exception message")) { "error message with thrown exception" }
    ```

