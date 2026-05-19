package ravenworks.fizz.engine.lock;


/**
 * @author Raven
 */
public interface SchedulerLock {

    void init();

    PulseResult pulse();

    void release();


    enum PulseResult {
        ACQUIRED,
        RENEWED,
        LOST,
        FAILED
    }

}
