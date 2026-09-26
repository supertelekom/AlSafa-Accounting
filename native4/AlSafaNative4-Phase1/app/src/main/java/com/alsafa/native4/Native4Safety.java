package com.alsafa.native4;

/** Hard boundary for alpha Android builds. */
public final class Native4Safety {
    public static final String TEST_STORE_PREFIX = "test-native4-";
    private Native4Safety() {}

    public static boolean isIsolatedStore(String storeId) {
        return storeId != null && storeId.startsWith(TEST_STORE_PREFIX);
    }

    public static void requireIsolatedStore(String storeId) {
        if (!isIsolatedStore(storeId)) {
            throw new IllegalStateException("Native 4 alpha is restricted to isolated test stores");
        }
    }
}
