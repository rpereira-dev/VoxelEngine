package com.grillecube.client.renderer.gui;

public interface GuiListenerMouseEnter<T extends Gui> {
	public void invokeMouseEnter(T gui, double mousex, double mousey);
}