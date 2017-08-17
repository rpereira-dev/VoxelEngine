/**
**	This file is part of the project https://github.com/toss-dev/VoxelEngine
**
**	License is available here: https://raw.githubusercontent.com/toss-dev/VoxelEngine/master/LICENSE.md
**
**	PEREIRA Romain
**                                       4-----7          
**                                      /|    /|
**                                     0-----3 |
**                                     | 5___|_6
**                                     |/    | /
**                                     1-----2
*/

package com.grillecube.client.renderer.gui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.lwjgl.opengl.GL11;

import com.grillecube.client.opengl.GLFWListenerChar;
import com.grillecube.client.opengl.GLFWListenerKeyPress;
import com.grillecube.client.opengl.GLFWWindow;
import com.grillecube.client.opengl.GLH;
import com.grillecube.client.opengl.object.GLTexture;
import com.grillecube.client.renderer.MainRenderer;
import com.grillecube.client.renderer.MainRenderer.GLTask;
import com.grillecube.client.renderer.Renderer;
import com.grillecube.client.renderer.gui.components.Gui;
import com.grillecube.client.renderer.gui.components.GuiLabel;
import com.grillecube.client.renderer.gui.components.GuiView;
import com.grillecube.client.renderer.gui.components.parameters.GuiTextParameterTextAdjustBox;
import com.grillecube.client.renderer.gui.font.Font;
import com.grillecube.client.renderer.gui.font.FontModel;
import com.grillecube.common.Taskable;
import com.grillecube.common.VoxelEngine;
import com.grillecube.common.VoxelEngine.Callable;
import com.grillecube.common.maths.Matrix4f;
import com.grillecube.common.resources.R;

public class GuiRenderer extends Renderer {
	/** rendering program */
	private ProgramFont programFont;
	private ProgramTexturedQuad programTexturedQuad;
	private ProgramColoredQuad programColoredQuad;

	/** fonts */
	public static Font DEFAULT_FONT;

	/** Fonts */
	private Map<String, Font> fonts;

	/** the main gui, parent of every other guis */
	private Gui mainGui;

	/** gui rendering list (sorted by layers) */
	private ArrayList<Gui> renderingList;
	// private Gui guiFocused;
	// private int guiFocusedIndex;

	/** listeners */
	private GLFWListenerChar charListener;
	private GLFWListenerKeyPress keyListener;

	public GuiRenderer(MainRenderer renderer) {
		super(renderer);
	}

	@Override
	public void initialize() {
		this.fonts = new HashMap<String, Font>();
		this.programColoredQuad = new ProgramColoredQuad();
		this.programTexturedQuad = new ProgramTexturedQuad();
		this.programFont = new ProgramFont();
		this.mainGui = new GuiView() {

			@Override
			protected void onInitialized(GuiRenderer renderer) {
			}

			@Override
			protected void onDeinitialized(GuiRenderer renderer) {
			}

			@Override
			protected void onUpdate(float x, float y, boolean pressed) {
			}

			@Override
			public void onAddedTo(Gui gui) {
			}

			@Override
			public void onRemovedFrom(Gui gui) {
			}

		};
		this.renderingList = new ArrayList<Gui>();
		this.loadFonts();
		this.createListeners();
	}

	@Override
	public void deinitialize() {
		this.mainGui.deinitialize(this);
		this.fonts.clear();
		this.programColoredQuad.delete();
		this.programTexturedQuad.delete();
		this.programFont.delete();
		this.getParent().getGLFWWindow().removeKeyPressListener(this.keyListener);
		this.getParent().getGLFWWindow().removeCharListener(this.charListener);
	}

	private final void createListeners() {
		this.keyListener = new GLFWListenerKeyPress() {
			@Override
			public void invokeKeyPress(GLFWWindow glfwWindow, int key, int scancode, int mods) {
				mainGui.onKeyPressed(glfwWindow, key, scancode, mods);
			}
		};

		this.charListener = new GLFWListenerChar() {
			@Override
			public void invokeChar(GLFWWindow window, int codepoint) {
				mainGui.onCharPressed(window, codepoint);
			}
		};
		this.getParent().getGLFWWindow().addCharListener(this.charListener);
	}

	/** load every fonts */
	private final void loadFonts() {

		DEFAULT_FONT = this.getFont("Calibri");
		// DEFAULT_FONT = this.getFont("Pokemon");
	}

	public final Font registerFont(String name) {
		Font font = new Font(R.getResPath("font/" + name));
		this.fonts.put(name, font);
		return (font);
	}

	public Font getFont(String fontname) {
		Font font = this.fonts.get(fontname);
		if (font == null) {
			return (this.registerFont(fontname));
		}
		return (font);
	}

	@Override
	public void preRender() {
		GL11.glDisable(GL11.GL_DEPTH_TEST);
		GL11.glEnable(GL11.GL_BLEND);
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
	}

	@Override
	public void postRender() {
		GL11.glDisable(GL11.GL_BLEND);
	}

	@Override
	public void render() {
		// sort the guis by there weights
		this.renderingList.clear();
		this.addGuisToRenderingList(this.mainGui);
		this.renderingList.sort(Gui.WEIGHT_COMPARATOR);

		// render them in the correct order
		for (Gui gui : this.renderingList) {
			gui.render(this);
		}
	}

	/** a recursive helper to generate rendering list */
	private final void addGuisToRenderingList(Gui parent) {
		if (parent.getChildren() != null) {
			for (Gui child : parent.getChildren()) {
				this.renderingList.add(child);
				this.addGuisToRenderingList(child);
			}
		}
	}

	public void renderFontModel(FontModel model, Matrix4f transfMatrix) {
		this.programFont.useStart();
		this.programFont.bindFontModel(model, transfMatrix);
		model.render();
	}

	public void renderTexturedQuad(GLTexture glTexture, float ux, float uy, float vx, float vy,
			Matrix4f transformMatrix) {
		this.programTexturedQuad.useStart();
		this.programTexturedQuad.loadQuadTextured(glTexture, ux, uy, vx, vy, transformMatrix);
		GLH.glhDrawArrays(GL11.GL_POINTS, 0, 1);
	}

	public void renderColoredQuad(float r, float g, float b, float a, Matrix4f transformMatrix) {
		this.programColoredQuad.useStart();
		this.programColoredQuad.loadQuadColored(r, g, b, a, transformMatrix);
		GLH.glhDrawArrays(GL11.GL_POINTS, 0, 1);
	}

	private void updateViews() {

		GLFWWindow window = this.getParent().getGLFWWindow();
		float mx = (float) (window.getMouseX() / window.getWidth());
		float my = (float) (1.0f - window.getMouseY() / window.getHeight());
		boolean pressed = window.isMouseLeftPressed();
		this.mainGui.update(mx, my, pressed);
	}

	@Override
	public void getTasks(VoxelEngine engine, ArrayList<Callable<Taskable>> tasks) {
		// TODO : generate rendering list here
		this.updateViews();
		// tasks.add(engine.new Callable<Taskable>() {
		//
		// @Override
		// public Taskable call() throws Exception {
		// updateViews();
		// return (GuiRenderer.this);
		// }
		//
		// @Override
		// public String getName() {
		// return ("GuiRenderer guis update");
		// }
		// });
	}

	/** add a view to render */
	public final void addGui(Gui gui) {
		this.mainGui.addChild(gui);
	}

	/** remove a view to render */
	public final void removeGui(Gui gui) {
		this.mainGui.removeChild(gui);
	}

	/** toast a message on the screen */
	public void toast(String text, Font font, float r, float g, float b, float a, int time) {

		GuiLabel lbl = new GuiLabel() {
			int timer = time;

			@Override
			protected void onRender(GuiRenderer renderer) {
				super.onRender(renderer);

				// weird trick, apparently it doesnt compile otherwise
				final GuiLabel thisGui = this;
				this.timer--;
				if (this.timer <= 0) {
					GuiRenderer.this.getParent().addGLTask(new GLTask() {
						@Override
						public void run() {
							GuiRenderer.this.removeGui(thisGui);
						}
					});
				}
			}
		};
		lbl.setFontColor(r, g, b, a);
		// lbl.setFontSize(1.0f, 1.0f);
		lbl.setText(text);
		lbl.setBoxCenterPosition(0.5f, 0.5f);
		lbl.addParameter(new GuiTextParameterTextAdjustBox());

		// gui.startAnimation(new GuiAnimationTextHoverScale<GuiLabel>(1.1f));
		this.addGui(lbl);
	}

	public void toast(String str, float r, float g, float b, float a) {
		this.toast(str, r, g, b, a, 30);
	}

	public void toast(String str) {
		this.toast(str, 0, 1, 0, 1, 30);
	}

	public void toast(String str, int time) {
		this.toast(str, 0, 1, 0, 1, time);
	}

	public void toast(String str, boolean good) {
		if (good) {
			this.toast(str, 0, 1, 0, 1, 90);
		} else {
			this.toast(str, 1, 0, 0, 1, 90);
		}
	}

	public void toast(String str, float r, float g, float b, float a, int time) {
		this.toast(str, GuiRenderer.DEFAULT_FONT, r, g, b, a, time);
	}

	public void onWindowResize(int width, int height) {
		this.mainGui.onWindowResized(width, height);
	}
}
