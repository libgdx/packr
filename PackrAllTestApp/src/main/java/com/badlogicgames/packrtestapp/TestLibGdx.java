package com.badlogicgames.packrtestapp;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import org.lwjgl.opengl.GL11;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

class TestLibGdx implements ApplicationListener {
   boolean firstRender = true;

   @Override public void create() {
      System.out.println("Created libGDX!");
   }

   @Override public void resize(int width, int height) {
      System.out.println("Resized libGDX!");
   }

   @Override public void render() {
      //		System.out.println("Rendering libGDX!");
      Gdx.gl.glClearColor(1f, 1f, 1f, 1f);
      Gdx.gl.glClear(GL11.GL_COLOR_BUFFER_BIT);
      if(firstRender){
         firstRender = false;
         System.out.println("Going to try some Swing!");
         SwingUtilities.invokeLater(()->{
            JFrame frame = new JFrame("hello world");
            frame.setSize(300, 200);
            frame.setLocationRelativeTo(null);
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            frame.setVisible(true);
         });
      }
   }

   @Override public void pause() {

   }

   @Override public void resume() {

   }

   @Override public void dispose() {
      System.out.println("Dispose called");
   }
}
