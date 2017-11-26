
package com.grillecube.client.renderer.model.editor.camera;

import com.grillecube.client.renderer.gui.event.GuiEventKeyPress;
import com.grillecube.client.renderer.gui.event.GuiEventMouseScroll;
import com.grillecube.client.renderer.model.editor.gui.GuiModelView;
import com.grillecube.client.renderer.model.editor.mesher.EditableModel;
import com.grillecube.client.renderer.model.instance.ModelInstance;

public abstract class CameraTool {

	protected final GuiModelView guiModelView;
	protected boolean requestPanelsRefresh;

	public CameraTool(GuiModelView guiModelView) {
		this.guiModelView = guiModelView;
	}

	public abstract String getName();

	public final void update() {
		this.guiModelView.getWorldRenderer().getLineRendererFactory().removeAllLines();
		this.onUpdate();
	}

	protected void onUpdate() {
	}

	public void onKeyPress(GuiEventKeyPress<GuiModelView> event) {
	}

	public void onLeftPressed() {
	}

	public void onLeftReleased() {
	}

	public void onMouseScroll(GuiEventMouseScroll<GuiModelView> event) {
	}

	public void onRightPressed() {
	}

	public void onRightReleased() {
	}

	public void onMouseMove() {
	}

	public final GuiModelView getGuiModelView() {
		return (this.guiModelView);
	}

	public final float getBlockSizeUnit() {
		return (this.getModel() == null ? 1.0f : this.getModel().getBlockSizeUnit());
	}

	public final ModelInstance getModelInstance() {
		return (this.guiModelView.getSelectedModelInstance());
	}

	public final EditableModel getModel() {
		return (this.guiModelView.getSelectedModel());
	}

	public final void requestPanelsRefresh() {
		this.requestPanelsRefresh = true;
	}

	public final boolean requestedPanelRefresh() {
		return (this.requestPanelsRefresh);
	}

	public final ModelEditorCamera getCamera() {
		return (this.guiModelView.getCamera());
	}
}