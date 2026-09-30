package io.github.phlekies.smarthome.simulation;

/** Progress and cancellation hook for long-running computations. Must be thread-safe. */
public interface ComputationMonitor {

    ComputationMonitor NONE = new ComputationMonitor() {
        @Override
        public void progress(long done, long total) {
        }

        @Override
        public boolean isCancelled() {
            return false;
        }
    };

    void progress(long done, long total);

    boolean isCancelled();
}
