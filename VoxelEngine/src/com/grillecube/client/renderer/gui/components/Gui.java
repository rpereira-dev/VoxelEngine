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

package com.grillecube.client.renderer.gui.components;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;

import com.grillecube.client.opengl.GLFWWindow;
import com.grillecube.client.renderer.MainRenderer;
import com.grillecube.client.renderer.gui.GuiRenderer;
import com.grillecube.client.renderer.gui.animations.GuiAnimation;
import com.grillecube.client.renderer.gui.components.parameters.GuiParameter;
import com.grillecube.client.renderer.gui.listeners.GuiListenerChar;
import com.grillecube.client.renderer.gui.listeners.GuiListenerKeyPress;
import com.grillecube.client.renderer.gui.listeners.GuiListenerMouseEnter;
import com.grillecube.client.renderer.gui.listeners.GuiListenerMouseExit;
import com.grillecube.client.renderer.gui.listeners.GuiListenerMouseHover;
import com.grillecube.client.renderer.gui.listeners.GuiListenerMouseLeftPress;
import com.grillecube.client.renderer.gui.listeners.GuiListenerMouseLeftRelease;
import com.grillecube.common.maths.Matrix4f;
import com.grillecube.common.maths.Vector2f;
import com.grillecube.common.maths.Vector4f;

/**
 * 
 * Main Gui class.
 *
 * A Gui can have parent / children.
 * 
 * It has a position, a rotation (relative to the axis poiting into the screen),
 * and a scaling. These value are relative to the Gui parent referential (or to
 * the screen referential if it has no parent)
 * 
 * 
 *
 * @author Romain
 *
 */
public abstract class Gui {

	/** default colors */
	public static final Vector4f COLOR_WHITE = new Vector4f(0.9f, 0.9f, 0.9f, 1.0f);
	public static final Vector4f COLOR_BLACK = new Vector4f(0.1f, 0.1f, 0.1f, 1.0f);
	public static final Vector4f COLOR_BLUE = new Vector4f(65 / 255.0f, 105 / 255.0f, 225 / 255.0f, 1.0f);
	public static final Vector4f COLOR_RED = new Vector4f(176 / 255.0f, 23 / 255.0f, 31 / 255.0f, 1.0f);
	public static final Vector4f COLOR_DARK_MAGENTA = new Vector4f(139 / 255.0f, 0 / 255.0f, 139 / 255.0f, 1.0f);

	/** mouse states (using bitwise operations) */
	public static final int STATE_INITIALIZED = (1 << 0);
	public static final int STATE_HOVERED = (1 << 1);
	public static final int STATE_FOCUSED = (1 << 2);
	public static final int STATE_FOCUS_REQUESTED = (1 << 3);
	public static final int STATE_LEFT_PRESSED = (1 << 4);
	public static final int STATE_VISIBLE = (1 << 5);
	private static final int STATE_SELECTED = (1 << 6);
	private static final int STATE_SELECTABLE = (1 << 7);
	private static final int STATE_ENABLED = (1 << 8);

	/** the transformation matrix, relative to the parent */
	private final Matrix4f guiToParentChangeOfBasis;
	private final Matrix4f guiToWindowChangeOfBasis;
	private final Matrix4f windowToGuiChangeOfBasis;
	private final Matrix4f guiToGLChangeOfBasis;

	/** gui custom attributes */
	private HashMap<String, Object> attributes;

	/** this rectangle relative to opengl axis (-1;-1) to (1;1) */
	private final Vector2f boxPos;
	private final Vector2f boxSize;
	private final Vector2f boxCenter;
	private float boxRot;

	/** guis of this view */
	private ArrayList<Gui> children;
	private final ArrayList<GuiTask> tasks;
	private Gui parent;

	private ArrayList<GuiParameter<Gui>> params;

	/** gui animations */
	private ArrayList<GuiAnimation<Gui>> animations;

	private ArrayList<GuiListenerMouseHover<?>> listeners_mouse_hover;
	private ArrayList<GuiListenerMouseEnter<?>> listeners_mouse_enter;
	private ArrayList<GuiListenerMouseExit<?>> listeners_mouse_exit;
	private ArrayList<GuiListenerMouseLeftRelease<?>> listeners_mouse_left_release;
	private ArrayList<GuiListenerMouseLeftPress<?>> listeners_mouse_left_press;
	private ArrayList<GuiListenerKeyPress<?>> listeners_key_press;
	private ArrayList<GuiListenerChar<?>> listeners_char;

	public static final Comparator<? super Gui> WEIGHT_COMPARATOR = new Comparator<Gui>() {
		@Override
		public int compare(Gui left, Gui right) {
			return (-left.getWeight() + right.getWeight());
		}
	};

	/** gui state */
	private int state;
	private float localAspectRatio = 1.0f;
	private float totalAspectRatio = 1.0f;
	private int weight;

	public Gui() {
		this.children = null;
		this.boxRot = 0.0F;
		this.guiToParentChangeOfBasis = new Matrix4f();
		this.guiToWindowChangeOfBasis = new Matrix4f();
		this.windowToGuiChangeOfBasis = new Matrix4f();
		this.guiToGLChangeOfBasis = new Matrix4f();
		this.boxPos = new Vector2f();
		this.boxCenter = new Vector2f();
		this.boxSize = new Vector2f();
		this.boxRot = 0.0f;
		this.setBox(0, 0, 1, 1, 0);
		this.params = null;
		this.animations = null;
		this.listeners_mouse_hover = null;
		this.listeners_mouse_enter = null;
		this.listeners_mouse_exit = null;
		this.listeners_mouse_left_release = null;
		this.listeners_mouse_left_press = null;
		this.listeners_key_press = null;
		this.listeners_char = null;
		this.state = 0;
		this.tasks = new ArrayList<GuiTask>();
		this.setVisible(true);
		this.setSelected(false);
		this.setSelectable(false);
		this.setEnabled(true);
	}

	/** a listener to the mouse hovering the gui */
	public void addListener(GuiListenerKeyPress<?> listener) {
		if (this.listeners_key_press == null) {
			this.listeners_key_press = new ArrayList<GuiListenerKeyPress<?>>();
		}
		this.listeners_key_press.add(listener);
	}

	/** a listener to the mouse hovering the gui */
	public void addListener(GuiListenerChar<?> listener) {
		if (this.listeners_char == null) {
			this.listeners_char = new ArrayList<GuiListenerChar<?>>();
		}
		this.listeners_char.add(listener);
	}

	/** a listener to the mouse hovering the gui */
	public void addListener(GuiListenerMouseHover<?> listener) {
		if (this.listeners_mouse_hover == null) {
			this.listeners_mouse_hover = new ArrayList<GuiListenerMouseHover<?>>();
		}
		this.listeners_mouse_hover.add(listener);
	}

	/** a listener to the mouse entering the gui */
	public void addListener(GuiListenerMouseEnter<?> listener) {
		if (this.listeners_mouse_enter == null) {
			this.listeners_mouse_enter = new ArrayList<GuiListenerMouseEnter<?>>();
		}
		this.listeners_mouse_enter.add(listener);
	}

	/** a listener to the mouse entering the gui */
	public void addListener(GuiListenerMouseExit<?> listener) {
		if (this.listeners_mouse_exit == null) {
			this.listeners_mouse_exit = new ArrayList<GuiListenerMouseExit<?>>();
		}
		this.listeners_mouse_exit.add(listener);
	}

	/** a listener to the mouse entering the gui */
	public void addListener(GuiListenerMouseLeftRelease<?> listener) {
		if (this.listeners_mouse_left_release == null) {
			this.listeners_mouse_left_release = new ArrayList<GuiListenerMouseLeftRelease<?>>();
		}
		this.listeners_mouse_left_release.add(listener);
	}

	/** a listener to the mouse entering the gui */
	public void addListener(GuiListenerMouseLeftPress<?> listener) {
		if (this.listeners_mouse_left_press == null) {
			this.listeners_mouse_left_press = new ArrayList<GuiListenerMouseLeftPress<?>>();
		}
		this.listeners_mouse_left_press.add(listener);
	}

	/** a listener to the mouse hovering the gui */
	public void removeListener(GuiListenerKeyPress<?> listener) {
		if (this.listeners_key_press == null) {
			return;
		}
		this.listeners_key_press.remove(listener);
	}

	/** a listener to the mouse hovering the gui */
	public void removeListener(GuiListenerChar<?> listener) {
		if (this.listeners_char == null) {
			return;
		}
		this.listeners_char.remove(listener);
	}

	/** a listener to the mouse hovering the gui */
	public void removeListener(GuiListenerMouseHover<?> listener) {
		if (this.listeners_mouse_hover == null) {
			return;
		}
		this.listeners_mouse_hover.remove(listener);
	}

	/** a listener to the mouse entering the gui */
	public void removeListener(GuiListenerMouseEnter<?> listener) {
		if (this.listeners_mouse_enter == null) {
			return;
		}
		this.listeners_mouse_enter.remove(listener);
	}

	/** a listener to the mouse entering the gui */
	public void removeListener(GuiListenerMouseExit<?> listener) {
		if (this.listeners_mouse_exit == null) {
			return;
		}
		this.listeners_mouse_exit.remove(listener);
	}

	/** a listener to the mouse entering the gui */
	public void removeListener(GuiListenerMouseLeftRelease<?> listener) {
		if (this.listeners_mouse_left_release == null) {
			return;
		}
		this.listeners_mouse_left_release.remove(listener);
	}

	/** a listener to the mouse entering the gui */
	public void removeListener(GuiListenerMouseLeftPress<?> listener) {
		if (this.listeners_mouse_left_press == null) {
			return;
		}
		this.listeners_mouse_left_press.remove(listener);
	}

	/**
	 * start an animation
	 * 
	 * @return the animation id
	 */
	@SuppressWarnings("unchecked")
	public int startAnimation(GuiAnimation<? extends Gui> animation) {
		if (this.animations == null) {
			this.animations = new ArrayList<GuiAnimation<Gui>>();
		}
		this.animations.add((GuiAnimation<Gui>) animation);
		((GuiAnimation<Gui>) animation).restart(this);
		return (this.animations.size() - 1);
	}

	/** stop an animation by it id */
	public void stopAnimation(int index) {
		if (this.animations != null && index >= 0 && index < this.animations.size()) {
			this.animations.remove(index);
			if (this.animations.size() == 0) {
				this.animations = null;
			}
		}
	}

	/** stop every animation with the given class */
	public void stopAnimation(Class<?> clazz) {
		if (this.animations == null) {
			return;
		}

		for (int i = 0; i < this.animations.size(); i++) {
			GuiAnimation<?> animation = this.animations.get(i);

			if (animation == null) {
				continue;
			}

			if (animation.getClass().equals(clazz)) {
				this.stopAnimation(i);
			}
		}
	}

	public void setBoxPosition(float x, float y) {
		this.setBoxPosition(x, y, true);
	}

	protected void setBoxPosition(float x, float y, boolean runParameters) {
		this.setBox(x, y, this.getBoxWidth(), this.getBoxHeight(), this.getBoxRotation(), runParameters);
	}

	/** set the gui text center, if using PARAM_CENTER */
	public final void setBoxCenterPosition(float xcenter, float ycenter) {
		this.setBoxCenter(xcenter, ycenter, this.getBoxWidth(), this.getBoxHeight(), this.getBoxRotation());
	}

	public final void setBoxCenter(float xcenter, float ycenter, float width, float height, float rot) {
		this.setBoxCenter(xcenter, ycenter, width, height, rot, true);
	}

	public final void setBoxCenter(float xcenter, float ycenter, float width, float height, float rot,
			boolean runParameters) {
		this.setBox(xcenter - width * 0.5f, ycenter - height * 0.5f, width, height, rot, runParameters);
	}

	/** set the x position, but do not run parameters to adjust it */
	public final void setBoxX(float x) {
		this.setBoxPosition(x, this.boxPos.y);
	}

	/** set the y position, but do not run parameters to adjust it */
	public final void setBoxY(float y) {
		this.setBoxPosition(this.boxPos.x, y);
	}

	/** set the width, but do not run parameters to adjust it */
	public void setBoxWidth(float width) {
		this.setBoxSize(width, this.boxSize.y);
	}

	/** set the height, but do not run parameters to adjust it */
	public void setBoxHeight(float height) {
		this.setBoxSize(this.boxSize.x, height);
	}

	public void setBoxSize(float width, float height) {
		this.setBoxSize(width, height, true);
	}

	public final void setBoxSize(float width, float height, boolean runParameters) {
		this.setBox(this.getBoxX(), this.getBoxY(), width, height, this.getBoxRotation(), runParameters);
	}

	/**
	 * MAIN SETTER FUNCTION.
	 * 
	 * Every setters end up calling this one.
	 * 
	 * It updates the transformation matrix
	 * 
	 * Coordinates and dimension follows (0, 0) (bot left) (1, 1) (top right)
	 * 
	 * @param x
	 * @param y
	 * @param width
	 * @param height
	 * @param rot
	 */
	public final void setBox(float x, float y, float width, float height, float rot) {
		this.setBox(x, y, width, height, rot, true);
	}

	public final void setBox(float x, float y, float width, float height, float rot, boolean runParameters) {

		if (width == 0.0f) {
			width = 0.000000000001f;
		}
		if (height == 0.0f) {
			height = 0.000000000001f;
		}

		// positions
		this.boxPos.set(x, y);
		this.boxSize.set(width, height);
		this.boxRot = rot;
		this.boxCenter.set(x + width * 0.5f, y + height * 0.5f);

		// matrices are relative to GL referential
		this.guiToParentChangeOfBasis.setIdentity();
		this.guiToParentChangeOfBasis.translate(this.boxCenter.x, this.boxCenter.y, 0.0f);
		this.guiToParentChangeOfBasis.rotateZ(rot);
		this.guiToParentChangeOfBasis.translate(-this.boxCenter.x, -this.boxCenter.y, 0.0f);
		this.guiToParentChangeOfBasis.translate(x, y, 0.0f);
		this.guiToParentChangeOfBasis.scale(width, height, 1.0f);

		Matrix4f parentTransform = this.parent == null ? Matrix4f.IDENTITY : this.parent.guiToWindowChangeOfBasis;
		this.updateTransformationMatrices(parentTransform);

		// aspect ratio
		this.localAspectRatio = this.getBoxWidth() / this.getBoxHeight();
		this.updateAspectRatio(this.getParent() == null ? 1.0f : this.getParent().getTotalAspectRatio(), runParameters);

		this.onBoxSet(x, y, width, height, rot);

		if (runParameters) {
			this.runParameters();
		}
	}

	private void updateAspectRatio(float parentAspectRatio, boolean runParameters) {
		this.totalAspectRatio = parentAspectRatio * this.localAspectRatio;
		this.onAspectRatioUpdate(runParameters);
		if (this.children != null) {
			for (Gui gui : this.children) {
				gui.updateAspectRatio(this.totalAspectRatio, runParameters);
			}
		}
	}

	protected void onAspectRatioUpdate(boolean runParameters) {
		// TODO Auto-generated method stub
	}

	protected void onBoxSet(float x, float y, float width, float height, float rot) {
	}

	/** update transformation matrices */
	private final void updateTransformationMatrices(Matrix4f parentTransform) {

		// combine with parent transformation
		Matrix4f.mul(parentTransform, this.guiToParentChangeOfBasis, this.guiToWindowChangeOfBasis);
		Matrix4f.invert(this.guiToWindowChangeOfBasis, this.windowToGuiChangeOfBasis);
		Matrix4f.mul(MainRenderer.WINDOW_TO_GL_BASIS, this.guiToWindowChangeOfBasis, this.guiToGLChangeOfBasis);

		// finally, set it relative to opengl screen referential
		if (this.children != null) {
			for (Gui child : this.children) {
				child.updateTransformationMatrices(this.guiToWindowChangeOfBasis);
			}
		}
	}

	/**
	 * render the gui
	 * 
	 * @param program
	 */
	public final void render(GuiRenderer renderer) {

		if (!this.isInitialized()) {
			this.initialize(renderer);
		}

		this.onRender(renderer);
	}

	/** do the rendering of this gui */
	protected void onRender(GuiRenderer guiRenderer) {

	}

	/** initialize the gui */
	protected final void initialize(GuiRenderer renderer) {
		this.setState(STATE_INITIALIZED);
		this.onInitialized(renderer);

		if (this.children != null) {
			for (Gui gui : this.children) {
				gui.initialize(renderer);
			}
		}
	}

	/** initialize the gui: this function is call in opengl main thread */
	protected abstract void onInitialized(GuiRenderer renderer);

	/** deinitialize the gui */
	public final void deinitialize(GuiRenderer renderer) {
		if (!this.hasState(STATE_INITIALIZED)) {
			return;
		}

		this.unsetState(STATE_INITIALIZED);
		this.onDeinitialized(renderer);

		if (this.children != null) {
			for (Gui gui : this.children) {
				gui.deinitialize(renderer);
			}
		}
	}

	public final void deinitialize() {
		this.deinitialize(null);
	}

	/** deinitialize the gui: this function is call in opengl main thread */
	protected abstract void onDeinitialized(GuiRenderer renderer);

	/**
	 * update this gui, mouse (x, y) coordinates are relative to the window
	 * 
	 * @param x
	 *            : x
	 * @param y
	 *            : y
	 * @param pressed
	 *            : if mouse is left pressed
	 */
	public final void update(float x, float y, boolean pressed) {

		if (!this.isEnabled()) {
			return;
		}

		Vector4f mouse = new Vector4f(x, y, 0.0f, 1.0f);
		Matrix4f.transform(this.windowToGuiChangeOfBasis, mouse, mouse);
		this.updateState(mouse.x, mouse.y, pressed);
		this.onUpdate(mouse.x, mouse.y, pressed);
		this.updateAnimations();

		if (this.children != null) {
			for (Gui child : this.children) {
				child.update(x, y, pressed);
			}
		}

		for (int i = 0; i < this.tasks.size(); i++) {
			this.tasks.get(i).run();
		}
		this.tasks.clear();
		this.tasks.trimToSize();
	}

	/** a task to be run at the end of the gui update */
	public interface GuiTask {
		public void run();
	}

	/** add a task to be run at the end of the gui update */
	public final void addTask(GuiTask task) {
		this.tasks.add(task);
	}

	private void updateAnimations() {
		if (this.animations != null) {
			for (int i = 0; i < this.animations.size(); i++) {
				GuiAnimation<Gui> animation = this.animations.get(i);
				if (animation == null || animation.update(this)) {
					this.animations.remove(i);
					continue;
				}
				++i;
			}
		}
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public final void onCharPressed(GLFWWindow window, int codepoint) {
		if (this.listeners_char != null) {
			for (GuiListenerChar listener : this.listeners_char) {
				listener.invokeCharPress(this, window, codepoint);
			}
		}
		if (this.children != null) {
			for (Gui child : this.children) {
				child.onCharPressed(window, codepoint);
			}
		}
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public final void onKeyPressed(GLFWWindow glfwWindow, int key, int scancode, int mods) {
		if (this.listeners_key_press != null) {
			for (GuiListenerKeyPress listener : this.listeners_key_press) {
				listener.invokeKeyPress(this, glfwWindow, key, scancode, mods);
			}
		}
		if (this.children != null) {
			for (Gui child : this.children) {
				child.onKeyPressed(glfwWindow, key, scancode, mods);
			}
		}
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private void updateState(float x, float y, boolean pressed) {

		boolean mouse_in = (x >= 0.0f && x < 1.0f && y >= 0.0f && y <= 1.0f);

		// if mouse is in the gui bounding box
		if (mouse_in) {
			// raise hover event
			if (this.listeners_mouse_hover != null) {
				for (GuiListenerMouseHover listener : this.listeners_mouse_hover) {
					listener.invokeMouseHover(this, pressed, x, y);
				}
			}
		}

		// if mouse is not in the gui bounding box, but used to be
		if (this.isHovered() && !mouse_in) {
			this.setHovered(false);

			// raise exit event
			if (this.listeners_mouse_exit != null) {
				for (GuiListenerMouseExit listener : this.listeners_mouse_exit) {
					listener.invokeMouseExit(this, x, y);
				}
			}
		} else if (!this.isHovered() && mouse_in) {
			// if mouse is in the gui bounding box, but wasnt earlier
			this.setHovered(true);

			if (this.listeners_mouse_enter != null) {
				for (GuiListenerMouseEnter listener : this.listeners_mouse_enter) {
					listener.invokeMouseEnter(this, x, y);
				}
			}
		}

		if (this.isLeftPressed() && !pressed) {
			this.setLeftPressed(false);

			if (this.listeners_mouse_left_release != null) {
				for (GuiListenerMouseLeftRelease listener : this.listeners_mouse_left_release) {
					listener.invokeMouseLeftRelease(this, x, y);
				}
			}

			if (this.isSelectable()) {
				this.setSelected(!this.isSelected());
			} else {
				this.setSelected(false);
			}

		} else if (!this.hasState(STATE_LEFT_PRESSED) && pressed && this.isHovered()) {
			this.setState(STATE_LEFT_PRESSED);
			if (this.listeners_mouse_left_press != null) {
				for (GuiListenerMouseLeftPress listener : this.listeners_mouse_left_press) {
					listener.invokeMouseLeftPress(this, x, y);
				}
			}

			if (!this.isSelectable()) {
				this.setSelected(true);
			}
		}
	}

	public void setVisible(boolean visible) {
		this.setState(STATE_VISIBLE, visible);
	}

	public final void setEnabled(boolean enabled) {
		this.setState(STATE_ENABLED, enabled);
	}

	public final void setSelectable(boolean isSelectable) {
		this.setState(STATE_SELECTABLE, isSelectable);
	}

	public final void setSelected(boolean isSelected) {
		this.setState(STATE_SELECTED, isSelected);
	}

	public final void setHovered(boolean isHovered) {
		this.setState(STATE_HOVERED, isHovered);
	}

	public final void setLeftPressed(boolean isLeftPressed) {
		this.setState(STATE_LEFT_PRESSED, isLeftPressed);
	}

	public final boolean isSelectable() {
		return (this.hasState(STATE_SELECTABLE));
	}

	public final boolean isSelected() {
		return (this.hasState(STATE_SELECTED));
	}

	public final boolean isEnabled() {
		return (this.hasState(STATE_ENABLED));
	}

	public final boolean isVisible() {
		return (this.hasState(STATE_VISIBLE));
	}

	/** return true if the mouse cursor is inside this gui */
	public final boolean isHovered() {
		return (this.hasState(STATE_HOVERED));
	}

	public boolean isLeftPressed() {
		return (this.hasState(STATE_LEFT_PRESSED));
	}

	protected abstract void onUpdate(float x, float y, boolean pressed);

	@SuppressWarnings("unchecked")
	public void addParameter(GuiParameter<?> parameter) {
		if (this.params == null) {
			this.params = new ArrayList<GuiParameter<Gui>>();
		}
		this.params.add((GuiParameter<Gui>) parameter);
		((GuiParameter<Gui>) parameter).run(this);
	}

	public void addParameters(GuiParameter<?>... parameters) {
		for (GuiParameter<?> parameter : parameters) {
			this.addParameter(parameter);
		}
	}

	/**
	 * run the parameter to update the gui position and size depending on it
	 * parameters You shall not have to call it, as it is call when setting a
	 * gui's size and position automatically
	 */
	public final void runParameters() {
		if (this.params == null) {
			return;
		}
		for (GuiParameter<Gui> param : this.params) {
			param.run(this);
		}
		if (this.children != null) {
			for (Gui child : this.children) {
				child.runParameters(); // TODO keep this?
			}
		}
	}

	public boolean hasParameter(GuiParameter<?> param) {
		if (this.params == null) {
			return (false);
		}
		return (this.params.contains(param));
	}

	public float getBoxCenterX() {
		return (this.boxCenter.x);
	}

	public float getBoxCenterY() {
		return (this.boxCenter.y);
	}

	public float getBoxX() {
		return (this.boxPos.x);
	}

	public float getBoxY() {
		return (this.boxPos.y);
	}

	public float getBoxWidth() {
		return (this.boxSize.x);
	}

	public float getBoxHeight() {
		return (this.boxSize.y);
	}

	public boolean hasFocus() {
		return (this.hasState(STATE_FOCUSED));
	}

	public void setFocus(boolean value) {
		this.setState(STATE_FOCUSED);
		this.setFocusRequest(value);
		if (value) {
			this.onFocusGained();
		} else {
			this.onFocusLost();
		}
	}

	protected void onFocusLost() {
	}

	protected void onFocusGained() {
	}

	public void setFocusRequest(boolean value) {
		if (value) {
			this.setState(STATE_FOCUS_REQUESTED);
		} else {
			this.unsetState(STATE_FOCUS_REQUESTED);
		}
	}

	public final boolean hasFocusRequest() {
		return (this.hasState(STATE_FOCUS_REQUESTED));
	}

	public final float getBoxRotation() {
		return (this.boxRot);
	}

	public final void setBoxRotation(float rot) {
		this.setBoxRotation(rot, true);
	}

	public final void setBoxRotation(float rot, boolean runParameters) {
		this.setBox(this.getBoxX(), this.getBoxY(), this.getBoxWidth(), this.getBoxHeight(), rot, runParameters);
	}

	public final Gui getParent() {
		return (this.parent);
	}

	public final ArrayList<Gui> getChildren() {
		return (this.children);
	}

	/** add a child to this gui */
	public final void addChild(Gui gui) {
		this.addChild(this.children == null ? 0 : this.children.size(), gui);
	}

	public final void addChild(int position, Gui gui) {
		gui.parent = this;
		if (this.children == null) {
			this.children = new ArrayList<Gui>();
		}
		this.children.add(position, gui);
		gui.updateTransformationMatrices(this.guiToWindowChangeOfBasis);
		gui.updateAspectRatio(this.getTotalAspectRatio(), false);
		gui.setWeight(this.getWeight() + 1);
		gui.onAddedTo(this);
	}

	/** remove a child from this gui */
	public final void removeChild(Gui gui) {
		if (this.children == null) {
			return;
		}
		this.children.remove(gui);
		if (this.children.size() == 0) {
			this.children = null;
		}
		gui.onRemovedFrom(gui);
	}

	/** called when this gui is added to another gui */
	public abstract void onAddedTo(Gui gui);

	/** called when this gui is removed from another gui */
	public abstract void onRemovedFrom(Gui gui);

	/** return true if this Gui has been initialized */
	public final boolean isInitialized() {
		return (this.hasState(STATE_INITIALIZED));
	}

	private final boolean hasState(int state) {
		return ((this.state & state) == state);
	}

	private final void setState(int state) {
		this.state = this.state | state;
	}

	private final void setState(int state, boolean enabled) {
		if (enabled) {
			this.setState(state);
		} else {
			this.unsetState(state);
		}
	}

	private final void unsetState(int state) {
		this.state = this.state & ~state;
	}

	@SuppressWarnings("unused")
	private final void swapState(int state) {
		this.state = this.state ^ state;
	}

	public final Matrix4f getGuiToWindowChangeOfBasis() {
		return (this.guiToWindowChangeOfBasis);
	}

	public final Matrix4f getWindowToGuiChangeOfBasis() {
		return (this.windowToGuiChangeOfBasis);
	}

	public final Matrix4f getGuiToParentChangeOfBasis() {
		return (this.guiToParentChangeOfBasis);
	}

	public final Matrix4f getGuiToGLChangeOfBasis() {
		return (this.guiToGLChangeOfBasis);
	}

	/**
	 * @return the aspect ratio of this gui relative to his parent
	 */
	public float getLocalAspectRatio() {
		return (this.localAspectRatio);
	}

	/**
	 * @return the aspect ratio of this gui relative to the window
	 */
	public float getTotalAspectRatio() {
		return (this.totalAspectRatio);
	}

	public void onWindowResized(int width, int height) {
		if (this.children != null) {
			for (Gui gui : this.children) {
				gui.onWindowResized(width, height);
			}
		}
	}

	public final int getWeight() {
		return (this.weight);
	}

	/**
	 * gui weight (the greater the weight is, the most this gui will be placed
	 * in foreground layers). This weight is relative to the window
	 */
	public final void setWeight(int weight) {
		this.weight = weight;
	}

	public final Object getAttribute(String attrID) {
		return (this.attributes == null ? null : this.attributes.get(attrID));
	}

	public final void setAttribute(String attrID, Object attribute) {
		if (this.attributes == null) {
			this.attributes = new HashMap<String, Object>();
			this.attributes.put(attrID, attribute);
		}
	}
}
