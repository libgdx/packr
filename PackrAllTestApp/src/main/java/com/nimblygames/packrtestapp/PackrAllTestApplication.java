/*
 * Copyright 2020 See AUTHORS file
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.nimblygames.packrtestapp;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Simple hello world application for testing the packr launcher.
 */
public class PackrAllTestApplication {
    /**
     * Main CLI entrance.
     *
     * @param args ignored
     * @throws IOException if an IO error occurs
     */
    public static void main(String[] args) throws IOException, InterruptedException {
        System.out.println("Hello world!");
        System.out.println("Running from java.version=" + System.getProperty("java.version"));

        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            System.out.println("Received uncaught exception in thread.getName()=" + thread.getName() + ", Thread.currentThread().getName()=" + Thread.currentThread().getName());
            throwable.printStackTrace(System.out);
        });

        Files.lines(Paths.get("application-resources").resolve("fake-resource.txt"))
                .forEach(resourceLine -> System.out.println("Loaded resource line: " + resourceLine));

        throw new RuntimeException("Testing uncaught exception handler. Thrown from the main thread, Thread.currentThread().getName()=" + Thread.currentThread().getName());
    }
}
