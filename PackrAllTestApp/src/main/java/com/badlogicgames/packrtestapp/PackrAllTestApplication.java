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

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import org.lwjgl.opengl.GL11;

import java.awt.Toolkit;
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
		  System.out.println("EventQueue=" + Toolkit.getDefaultToolkit().getSystemEventQueue());

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

		 //		  try (Stream<String> lineStream = Files.lines(Paths.get("application-resources").resolve("fake-resource.txt"))) {
		 //				lineStream.forEach(resourceLine -> System.out.println("Loaded resource line: " + resourceLine));
		 //		  }

		 Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
		 config.setTitle("libGdx test");
		 config.setWindowedMode(800, 480);
		 new Lwjgl3Application(new TestLibGdx(), config);

		 throw new RuntimeException("Testing uncaught exception handler. Thrown from the main thread, Thread.currentThread().getName()=" +
				 Thread.currentThread().getName());
	 }
}

class TestLibGdx implements ApplicationListener {

	@Override public void create() {

	}

	@Override public void resize(int width, int height) {

	}

	@Override public void render() {
		Gdx.gl.glClearColor(1f, 1f, 1f, 1f);
		Gdx.gl.glClear(GL11.GL_COLOR_BUFFER_BIT);
	}

	@Override public void pause() {

	}

	@Override public void resume() {

	}

	@Override public void dispose() {

	}
}
