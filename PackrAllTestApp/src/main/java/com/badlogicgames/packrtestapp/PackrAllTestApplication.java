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

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;

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
//		 SwingUtilities.invokeLater(()->{
//			 System.out.println("EventQueue=" + Toolkit.getDefaultToolkit().getSystemEventQueue());
//		 });


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

//		 SwingUtilities.invokeLater(()->{
//			 JFrame frame = new JFrame("hi");
//			 frame.getContentPane().add(new JLabel("hello Jojo"));
//			 frame.pack();
//			 frame.setSize(300, 200);
//			 frame.setLocationRelativeTo(null);
//			 frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
//			 frame.setVisible(true);
//			 System.out.println("Launched jframe");
//		 });
		 System.out.flush();
		 System.err.flush();

//		 GLFW.glfwInitHint(GLFW.GLFW_COCOA_CHDIR_RESOURCES, GLFW.GLFW_FALSE);
//		 GLFW.glfwInitHint(GLFW.GLFW_COCOA_MENUBAR, GLFW.GLFW_FALSE);
//		 GLFW.glfwInitHint(GLFW.GLFW_JOYSTICK_HAT_BUTTONS, GLFW.GLFW_FALSE);
//		 GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
//		 GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 2);
//		 GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
//		 GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GLFW.GLFW_TRUE);
//		 GLFW.glfwSetErrorCallback(new GLFWErrorCallback() {
//			 @Override public void invoke(int error, long description) {
//				 System.out.println("Got GLFW error " + error + ": " + description);
//			 }
//		 });
//		 System.out.println("Calling GLFW init.");
//		 final boolean init=GLFW.glfwInit();
//		 System.out.println("Done calling GLFW init=" + init);
//		 System.out.flush();
		 Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
		 config.useOpenGL3(true, 3, 2);
		 config.setResizable(true);
		 config.setTitle("libGdx test");
		 config.setWindowedMode(800, 600);

		 System.out.println("About to launch libGDX!");
		 new Lwjgl3Application(new TestLibGdx(), config);

		 throw new RuntimeException("Testing uncaught exception handler. Thrown from the main thread, Thread.currentThread().getName()=" +
				 Thread.currentThread().getName());
	 }
}

