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
 */

package com.badlogicgames.packrtestapp;

import com.badlogic.gdx.backends.lwjgl.LwjglApplication;
import com.badlogic.gdx.backends.lwjgl.LwjglApplicationConfiguration;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import java.io.IOException;
import java.nio.file.Path;
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
	 public static void main (String[] args) throws IOException, InterruptedException {
		 System.out.println("Thread = " + Thread.currentThread());

		  System.out.println("Hello world!");
		  System.out.println("Running from java.version=" + System.getProperty("java.version"));

		 Path javaExecutablePath = Paths.get(System.getProperty("java.home")).resolve("bin").resolve("java");
		 new ProcessBuilder(javaExecutablePath.toString(), "-version").inheritIO().start().waitFor();

		 Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
			 System.out.println("Received uncaught exception in thread.getName()=" +
					 thread.getName() +
					 ", Thread.currentThread().getName()=" +
					 Thread.currentThread().getName());
			 throwable.printStackTrace(System.out);
		 });

		 SwingUtilities.invokeLater(() -> {
			 JFrame frame = new JFrame("hi");
			 frame.getContentPane().add(new JLabel("hello Jojo"));
			 frame.pack();
			 frame.setSize(300, 200);
			 frame.setLocationRelativeTo(null);
			 frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
			 frame.setVisible(true);
			 System.out.println("Launched jframe");
		 });

		 LwjglApplicationConfiguration config = new LwjglApplicationConfiguration();
		 config.resizable = true;
		 config.title = "libGdx test";
		 config.width = 800;
		 config.height = 600;

		 System.out.println("About to launch libGDX!");
		 new LwjglApplication(new TestLibGdx(), config);

		 throw new RuntimeException("Testing uncaught exception handler. Thrown from the main thread, Thread.currentThread().getName()=" +
				 Thread.currentThread().getName());
	 }
}

