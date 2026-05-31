package com.aegiskv.test;

public class Assert {
    public static void assertEquals(Object expected, Object actual) {
        if (expected == null && actual == null) return;
        if (expected != null && expected.equals(actual)) return;
        throw new AssertionError("Expected: " + expected + " but got: " + actual);
    }
    
    public static void assertEquals(long expected, long actual) {
        if (expected != actual) throw new AssertionError("Expected: " + expected + " but got: " + actual);
    }
    
    public static void assertTrue(boolean condition) {
        if (!condition) throw new AssertionError("Condition was expected to be true");
    }
    
    public static void assertNotNull(Object obj) {
        if (obj == null) throw new AssertionError("Expected non-null object");
    }
    
    public static void assertNull(Object obj) {
        if (obj != null) throw new AssertionError("Expected null, got: " + obj);
    }
}
