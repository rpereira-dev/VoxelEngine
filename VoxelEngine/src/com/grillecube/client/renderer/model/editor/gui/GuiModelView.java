package com.grillecube.client.renderer.model.editor.gui;

import java.util.ArrayList;

import org.lwjgl.glfw.GLFW;

import com.grillecube.client.renderer.camera.CameraPicker;
import com.grillecube.client.renderer.camera.CameraProjective;
import com.grillecube.client.renderer.camera.Raycasting;
import com.grillecube.client.renderer.camera.RaycastingCallback;
import com.grillecube.client.renderer.gui.GuiRenderer;
import com.grillecube.client.renderer.gui.components.Gui;
import com.grillecube.client.renderer.gui.components.GuiViewDebug;
import com.grillecube.client.renderer.gui.components.GuiViewWorld;
import com.grillecube.client.renderer.gui.event.GuiEventKeyPress;
import com.grillecube.client.renderer.gui.event.GuiListener;
import com.grillecube.client.renderer.model.editor.ModelEditorCamera;
import com.grillecube.client.renderer.model.editor.ModelEditorMod;
import com.grillecube.client.renderer.model.editor.mesher.BlockData;
import com.grillecube.client.renderer.model.editor.mesher.EditableModel;
import com.grillecube.client.renderer.model.instance.ModelInstance;
import com.grillecube.common.maths.BoundingBox;
import com.grillecube.common.maths.Vector3f;
import com.grillecube.common.world.World;

/** the gui which displays the model */
public class GuiModelView extends Gui {

	private final ArrayList<ModelInstance> modelInstances;
	private final GuiViewWorld guiViewWorld;
	private final BoundingBox theBox;
	private int boxID;

	public GuiModelView() {
		super();
		this.modelInstances = new ArrayList<ModelInstance>();
		this.guiViewWorld = new GuiViewWorld();
		this.guiViewWorld.setHoverable(false);
		this.theBox = new BoundingBox();
	}

	@Override
	protected void onInitialized(GuiRenderer renderer) {
		int worldID = ModelEditorMod.WORLD_ID;
		CameraProjective camera = new ModelEditorCamera(renderer.getMainRenderer().getGLFWWindow());
		this.guiViewWorld.set(camera, worldID);
		this.guiViewWorld.initialize(renderer);
		this.boxID = this.guiViewWorld.getWorldRenderer().getLineRendererFactory().addBox(this.theBox);

		this.addChild(this.guiViewWorld);
		this.addChild(new GuiViewDebug(camera));

		this.addListener(Gui.ON_HOVERED_FOCUS_LISTENER);
		this.addListener(new GuiListener<GuiEventKeyPress<GuiModelView>>() {
			@Override
			public void invoke(GuiEventKeyPress<GuiModelView> event) {
				event.getGui().onKeyPress(event.getKey(), event.getMods(), event.getScancode());
			}
		});
	}

	@Override
	public void onUpdate() {
		super.onUpdate();
		this.updateModelInstances();
		this.updateHoveredBlock();
	}

	private final void updateModelInstances() {
		for (ModelInstance modelInstance : this.modelInstances) {
			modelInstance.getEntity().update();
			modelInstance.update();
		}
	}

	private final void updateHoveredBlock() {
		EditableModel model = getSelectedModel();
		if (model == null) {
			this.theBox.setMinSize(0, 0, 0, 1, 1, 1);
			this.guiViewWorld.getWorldRenderer().getLineRendererFactory().setBox(this.theBox, this.boxID);
			return;
		}

		float s = model.getBlockSizeUnit();
		ModelEditorCamera camera = (ModelEditorCamera) this.guiViewWorld.getWorldRenderer().getCamera();
		Vector3f ray = new Vector3f();
		CameraPicker.ray(ray, camera, this.getMouseX(), this.getMouseY());
		Raycasting.raycast(camera.getPosition(), ray, 256.0f, s, new RaycastingCallback() {
			@Override
			public boolean onRaycastCoordinates(int x, int y, int z, Vector3f face) {
				// System.out.println(x + " : " + y + " : " + z);
				if (y == model.getMinY() || model.getBlockData(x, y, z) != null) {
					theBox.setMinSize((x + face.x) * s, (y + face.y) * s, (z + face.z) * s, s, s, s);
					guiViewWorld.getWorldRenderer().getLineRendererFactory().setBox(theBox, boxID);
					return (true);
				}
				return (false);
			}
		});
	}

	protected void onKeyPress(int key, int mods, int scancode) {
		if (key == GLFW.GLFW_KEY_E) {
			EditableModel model = this.getSelectedModel();
			if (model != null) {
				model.setBlockData(new BlockData(), (int) this.theBox.getMin().x, (int) this.theBox.getMin().y,
						(int) this.theBox.getMin().z);
				model.generate();
			}
		}
	}

	private final ModelInstance getSelectedModelInstance() {
		return (((GuiModelEditor) this.getParent()).getSelectedModelInstance());
	}

	private final EditableModel getSelectedModel() {
		return (((GuiModelEditor) this.getParent()).getSelectedModel());
	}

	public final GuiViewWorld getGuiViewWorld() {
		return (this.guiViewWorld);
	}

	public final ModelEditorCamera getCamera() {
		return ((ModelEditorCamera) this.guiViewWorld.getWorldRenderer().getCamera());
	}

	public final World getWorld() {
		return (this.guiViewWorld.getWorldRenderer().getWorld());
	}

	public final void addModelInstance(ModelInstance modelInstance) {
		this.modelInstances.add(modelInstance);
		this.guiViewWorld.getWorldRenderer().getModelRendererFactory().addModelInstance(modelInstance);
	}

	public final void removeModelInstance(ModelInstance modelInstance) {
		this.modelInstances.remove(modelInstance);
		this.guiViewWorld.getWorldRenderer().getModelRendererFactory().removeModelInstance(modelInstance);
	}
}
