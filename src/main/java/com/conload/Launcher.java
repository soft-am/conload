package com.conload;
/**
 * Plain (non-JavaFX) entry point for the fat JAR.
 * Using a plain class as Main-Class avoids the JVM startup check that requires
 * JavaFX modules on the module path.
 * By the time App.main() is called, all JavaFX classes are on the classpath.
 */
public class Launcher {
    public static void main(String[] args) {
        App.main(args);
    }
}
