/*
 * Copyright (c) 2020 Nimbly Games, LLC all rights reserved
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
    *
    * @throws IOException if an IO error occurs
    */
   public static void main(String[] args) throws IOException {
      System.out.println("Hello world!");

      Files.lines(Paths.get("application-resources").resolve("fake-resource.txt")).forEach(resourceLine -> {
         System.out.println("Loaded resource line: " + resourceLine);
      });
   }
}
