package com.android.internal.ext;

public class EuiccGoogleHooks {

    private static final ThreadLocal<Integer> resourceFilteringSuppressionCounter =
            ThreadLocal.withInitial(() -> Integer.valueOf(0));

    public static boolean isResourceFilteringSuppressed() {
        return resourceFilteringSuppressionCounter.get() > 0;
    }

    public static class SuppressResourceFiltering implements AutoCloseable {

        public SuppressResourceFiltering() {
            var tl = resourceFilteringSuppressionCounter;
            int newValue = tl.get() + 1;
            if (newValue <= 0) {
                throw new IllegalStateException();
            }
            tl.set(newValue);
        }

        @Override
        public void close() {
            var tl = resourceFilteringSuppressionCounter;
            int newValue = tl.get() - 1;
            if (newValue < 0) {
                throw new IllegalStateException();
            }
            tl.set(newValue);
        }
    }
}
