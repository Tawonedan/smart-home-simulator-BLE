package io.github.phlekies.smarthome.simulation;

/** Progress and cancellation hook for long-running computations. Must be thread-safe. */
public interface ComputationMonitor {

    /** A monitor that ignores progress and never cancels. */
    ComputationMonitor NONE = new ComputationMonitor() {
        @Override
        public void progress(long done, long total) {
        }

        @Override
        public boolean isCancelled() {
            return false;
        }
    };

    /** Reports that {@code done} of {@code total} work units are finished. */
    void progress(long done, long total);

    /** Polled by the computation; returning true aborts it with a CancellationException. */
    boolean isCancelled();
}
