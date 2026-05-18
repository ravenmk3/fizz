package ravenworks.fizz.common.runtime;

/**
 * @author Raven
 */
public enum EventLoopState {
    NEW,
    RUNNING,
    SHUTTING_DOWN,
    TERMINATED
}
