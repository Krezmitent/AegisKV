package com.aegiskv.test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public class TestRunner {
    public static void main(String[] args) throws Exception {
        System.out.println("======================================");
        System.out.println("Starting AegisKV Native Test Suite...");
        System.out.println("======================================\n");
        
        Class<?>[] testClasses = {
            com.aegiskv.storage.OffHeapMemoryManagerTest.class,
            com.aegiskv.storage.IndexMapTest.class,
            com.aegiskv.wal.WriteAheadLogTest.class,
            com.aegiskv.network.ProtocolParserTest.class,
            com.aegiskv.AegisKVE2ETest.class
        };

        int passed = 0;
        int failed = 0;

        for (Class<?> clazz : testClasses) {
            System.out.println("Running tests in [" + clazz.getSimpleName() + "]");
            Object instance = clazz.getDeclaredConstructor().newInstance();
            
            for (Method method : clazz.getDeclaredMethods()) {
                if (method.getName().startsWith("test") && Modifier.isPublic(method.getModifiers())) {
                    try {
                        method.invoke(instance);
                        System.out.println("  [PASS] " + method.getName());
                        passed++;
                    } catch (Exception e) {
                        System.err.println("  [FAIL] " + method.getName());
                        if (e.getCause() != null) {
                            e.getCause().printStackTrace(System.err);
                        } else {
                            e.printStackTrace(System.err);
                        }
                        failed++;
                    }
                }
            }
            System.out.println();
        }
        
        System.out.println("======================================");
        System.out.println("Test Run Completed.");
        System.out.println("Passed: " + passed);
        System.out.println("Failed: " + failed);
        System.out.println("======================================");
        
        if (failed > 0) {
            System.exit(1);
        }
    }
}
