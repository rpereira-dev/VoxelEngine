package com.pot.common.world;

import com.grillecube.common.mod.IModResource;
import com.grillecube.common.mod.Mod;
import com.grillecube.common.resources.ResourceManager;

public class POTWorlds implements IModResource {
	/** default world id */
	public static WorldDefault DEFAULT;

	@Override
	public void load(Mod mod, ResourceManager manager) {

		DEFAULT = new WorldDefault();
		manager.getWorldManager().registerWorld(DEFAULT);
	}

	@Override
	public void unload(Mod mod, ResourceManager manager) {
	}

}
