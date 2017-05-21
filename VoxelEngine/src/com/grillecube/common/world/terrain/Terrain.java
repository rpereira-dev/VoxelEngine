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

package com.grillecube.common.world.terrain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Stack;

import com.grillecube.common.defaultmod.Blocks;
import com.grillecube.common.faces.Face;
import com.grillecube.common.maths.Maths;
import com.grillecube.common.maths.Vector3f;
import com.grillecube.common.maths.Vector3i;
import com.grillecube.common.world.World;
import com.grillecube.common.world.block.Block;
import com.grillecube.common.world.block.instances.BlockInstance;

public class Terrain {
	/** terrain states */
	public static final int STATE_VERTICES_UP_TO_DATE = 1 << 0;
	public static final int STATE_BLOCK_COMPRESSED = 1 << 1;
	public static final int STATE_LIGHT_COMPRESSED = 1 << 2;
	public static final int STATE_FACE_VISIBILTY_UP_TO_DATE = 1 << 3;

	/** terrain dimensions */
	// block size unit
	public static final float BLOCK_SIZE = 1.0f;

	// (and use 1 as implicit value to
	// optimize calculations)
	public static final float BLOCK_DEMI_SIZE = BLOCK_SIZE / 2.0f;

	public static Vector3f BLOCK_SIZE_VEC = new Vector3f(BLOCK_SIZE, BLOCK_SIZE, BLOCK_SIZE);

	public static final int DIM = 16; // terrain is 16x16x16
	public static final int DIM2 = DIM * DIM;
	public static final int DIM3 = DIM * DIM * DIM;
	public static final int DEMI_DIM = DIM / 2;
	public static final int MAX_BLOCK_INDEX = Terrain.DIM * Terrain.DIM * Terrain.DIM;
	public static final float SIZE_DIAGONAL = (float) Vector3f.distance(new Vector3f(0, 0, 0),
			new Vector3f(DIM * BLOCK_SIZE, DIM * BLOCK_SIZE, DIM * BLOCK_SIZE));

	public static float DIM_SIZE = DIM * BLOCK_SIZE;
	public static Vector3f TERRAIN_SIZE = new Vector3f(DIM_SIZE, DIM_SIZE, DIM_SIZE);
	public static float DEMI_SIZE_DIAGONAL = SIZE_DIAGONAL / 2.0f;
	public static float DEMI_DIM_SIZE = DIM_SIZE / 2.0f;

	/** neighboors */
	private Terrain[] neighboors;

	/** terrain location */
	protected TerrainLocation terrainLocation;

	/** block instances */
	private HashMap<Short, BlockInstance> blockInstances;

	/** block ids */
	protected short[] blocks;

	/** number of blocks visible */
	private short visibleBlocks;

	/** blocks */
	protected byte[] lights; // light values for every blocks

	/** world in which this terrain depends */
	private World world;

	private int state;

	/** if this terrain is fully passive, then compress it */
	private static final short TICK_TO_COMPRESS = 60 * 10;
	private short blockTickCounter;
	private short lightTickCounter;

	/** the lights lists */
	private Stack<LightNodeAdd> lightAddQueue;
	private Stack<LightNodeRemoval> lightRemovalQueue;

	/** which face can see another */
	private boolean[][] facesVisibility;

	public Terrain(int x, int y, int z) {
		this(new Vector3i(x, y, z));
	}

	public Terrain(Vector3i index) {
		this(new TerrainLocation(index));
	}

	public Terrain(TerrainLocation location) {
		this.terrainLocation = location;
		this.blocks = null;
		this.blockInstances = null;
		this.lights = null;
		this.neighboors = new Terrain[6];
		this.facesVisibility = new boolean[6][6];
		this.blockTickCounter = 0;
		this.lightTickCounter = 0;
		this.lightAddQueue = new Stack<LightNodeAdd>();
		this.lightRemovalQueue = new Stack<LightNodeRemoval>();
		for (Face a : Face.faces) {
			for (Face b : Face.faces) {
				this.facesVisibility[a.getID()][b.getID()] = true;
			}
		}
		this.setState(STATE_FACE_VISIBILTY_UP_TO_DATE);
		this.setState(STATE_VERTICES_UP_TO_DATE);
		this.unsetState(STATE_LIGHT_COMPRESSED);
		this.unsetState(STATE_BLOCK_COMPRESSED);
	}

	/******
	 * EVERY TERRAINS UPDATE RELATIVE FUNCTIONS (COMPLEX ALGORYTHM) ARE CALLED
	 * IN A SEPARATE THREAD. STARTS HERE
	 */
	/** update the terrain once */
	public void update() {
		this.checkFaceVisibility();
		this.updateBlockInstances();
		this.updateLights();
		// this.updateBlocks();
		this.compressMemory();
	}

	// public int gainBlock = 0;
	// public int gainLight = 0;

	/** compress memory if not requested for TICK_TO_COMPRESS ticks */
	private void compressMemory() {
		if (this.blocks != null && !this.hasState(STATE_BLOCK_COMPRESSED)) {
			if (this.blockTickCounter >= TICK_TO_COMPRESS) {
				// gainBlock = this.blocks.length;
				this.blocks = Compress.compressShortArray(this.blocks);
				// gainBlock -= this.blocks.length;
				this.setState(STATE_BLOCK_COMPRESSED);
			} else {
				++this.blockTickCounter;
			}
		}

		if (this.lights != null && !this.hasState(STATE_LIGHT_COMPRESSED)) {
			if (this.lightTickCounter >= TICK_TO_COMPRESS) {
				// gainLight = this.lights.length;
				this.lights = Compress.compressByteArray(this.lights);
				this.setState(STATE_LIGHT_COMPRESSED);
				// gainLight -= this.lights.length;
			} else {
				++this.lightTickCounter;
			}
		}
	}

	private void ensureBlockDecompressed() {
		if (this.hasState(STATE_BLOCK_COMPRESSED)) {
			this.unsetState(STATE_BLOCK_COMPRESSED);
			this.blocks = Compress.decompressShortArray(this.blocks, Terrain.MAX_BLOCK_INDEX);
			this.blockTickCounter = 0;
		}
	}

	private void ensureLightDecompressed() {
		if (this.hasState(STATE_LIGHT_COMPRESSED)) {
			this.unsetState(STATE_LIGHT_COMPRESSED);
			this.lights = Compress.decompressByteArray(this.lights, Terrain.MAX_BLOCK_INDEX);
			this.lightTickCounter = 0;
		}
	}

	/** terrain face visibility update */
	private void checkFaceVisibility() {
		if (!this.hasState(Terrain.STATE_FACE_VISIBILTY_UP_TO_DATE)) {
			this.updateFaceVisiblity();
		}
	}

	/**
	 * coordinates are relative to the current terrain instance, if the current
	 * light value is < the given one, then the given one is set
	 * 
	 * @param x
	 * @param y
	 * @param z
	 * @param lightvalue
	 * @return
	 */
	// private boolean updateLightValueAt(byte lightvalue, int... xyz) {
	// Terrain terrain = this.getRelativeTerrain(xyz);
	// if (terrain == null) {
	// return (false);
	// }
	// int index = terrain.getIndex(xyz);
	// if (terrain.getLightAt(index) < lightvalue) {
	// terrain.setLightAt(lightvalue, index);
	// return (true);
	// }
	// return (false);
	// }

	/** tick once block instances of this terrain */
	private void updateBlockInstances() {
		if (this.blockInstances == null) {
			return;
		}

		BlockInstance[] blockInstances = this.blockInstances.values()
				.toArray(new BlockInstance[this.blockInstances.size()]);
		for (BlockInstance blockInstance : blockInstances) {
			if (blockInstance == null) {
				continue;
			}
			blockInstance.update();
		}
	}

	/** tick once a random block of this terrain */
	private void updateBlocks() {

		if (this.blocks == null) {
			return;
		}

		short index = (short) (Maths.abs(this.getWorld().getRNG().nextInt()) % this.blocks.length);
		int z = this.getZFromIndex(index);
		int y = this.getYFromIndex(index, z);
		int x = this.getXFromIndex(index, y, z);

		Block block = this.getBlockAt(index);
		block.update(this, x, y, z);
	}

	/******
	 * EVERY TERRAINS UPDATE RELATIVE FUNCTIONS (COMPLEX ALGORYTHM) ARE CALLED
	 * IN A SEPARATE THREAD. END HERE
	 */

	public boolean hasState(int state) {
		return ((this.state & state) == state);
	}

	public void setState(int state) {
		this.state = this.state | state;
	}

	public void unsetState(int state) {
		this.state = this.state & ~(state);
	}

	public void switchState(int state) {
		this.state = this.state ^ state;
	}

	/**
	 * return the terrain at the given position, relative to this instance It
	 * fills the 'dst' Vector3i with it terrain-relative coordinates and return
	 * it
	 * 
	 * if the terrain didnt exists, it creates it
	 */
	public Terrain getRelativeTerrain(int... xyz) {
		Terrain terrain = this;

		// x test
		if (xyz[0] < 0) {
			do {
				xyz[0] += Terrain.DIM;
				terrain = terrain.getNeighbor(Face.FRONT);
			} while (xyz[0] < 0);
			if (terrain == null) {
				return (null);
			}
		} else if (xyz[0] >= Terrain.DIM) {
			do {
				xyz[0] -= Terrain.DIM;
				terrain = terrain.getNeighbor(Face.BACK);
			} while (xyz[0] >= Terrain.DIM);
			if (terrain == null) {
				return (null);
			}
		}

		// y test
		if (xyz[1] < 0) {
			do {
				xyz[1] += Terrain.DIM;
				terrain = terrain.getNeighbor(Face.BOT);
			} while (xyz[1] < 0);
			if (terrain == null) {
				return (null);
			}
		} else if (xyz[1] >= Terrain.DIM) {
			do {
				xyz[1] -= Terrain.DIM;
				terrain = terrain.getNeighbor(Face.TOP);
			} while (xyz[1] >= Terrain.DIM);
			if (terrain == null) {
				return (null);
			}
		}

		// z test
		if (xyz[2] < 0) {
			do {
				xyz[2] += Terrain.DIM;
				terrain = terrain.getNeighbor(Face.LEFT);
			} while (xyz[2] < 0);
			if (terrain == null) {
				return (null);
			}
		} else if (xyz[2] >= Terrain.DIM) {
			do {
				xyz[2] -= Terrain.DIM;
				terrain = terrain.getNeighbor(Face.RIGHT);
			} while (xyz[2] >= Terrain.DIM);
			if (terrain == null) {
				return (null);
			}
		}
		return (terrain);
	}

	public Block getBlock(int... xyz) {
		Terrain terrain = this.getRelativeTerrain(xyz);
		if (terrain == null) {
			return (Blocks.AIR);
		}
		return (terrain.getBlockAt(xyz[0], xyz[1], xyz[2]));
	}

	/** this function doesnt check bounds */
	public Block getBlockAt(int x, int y, int z) {
		return (this.getBlockAt(this.getIndex(x, y, z)));
	}

	public Block getBlockAt(short index) {
		if (this.blocks == null) {
			return (Blocks.AIR);
		}
		this.ensureBlockDecompressed();
		Block block = Blocks.getBlockByID(this.blocks[index]);
		return (block != null ? block : Blocks.AIR);

	}

	/** secure function to set a block relative to this terrain */
	public BlockInstance setBlock(Block block, int... xyz) {
		Terrain terrain = this.getRelativeTerrain(xyz);
		if (terrain == null) {
			return (null);
		}
		short index = this.getIndex(xyz[0], xyz[1], xyz[2]);
		return (terrain.setBlock(block, index, xyz[0], xyz[1], xyz[2]));
	}

	/** secure function to set a block relative to this terrain */
	public void setBlock(Block block, Vector3i pos) {
		this.setBlock(block, pos.x, pos.y, pos.z);
	}

	public BlockInstance setBlock(Block block, short index) {
		int z = this.getZFromIndex(index);
		int y = this.getYFromIndex(index, z);
		int x = this.getXFromIndex(index, y, z);
		return (this.setBlock(block, index, x, y, z));
	}

	/**
	 * WARNING : this function doest check bound, only use if you know what
	 * you're doing
	 * 
	 * @param block
	 * @param xyz
	 * @return
	 */
	public BlockInstance setBlockAt(Block block, int x, int y, int z) {
		return (this.setBlock(block, this.getIndex(x, y, z), x, y, z));
	}

	/** function to set a block to this terrain */
	public BlockInstance setBlock(Block block, short index, int... xyz) {

		int x = xyz[0];
		int y = xyz[1];
		int z = xyz[2];

		// if terrain was empty
		if (this.blocks == null) {
			// initialize it, fill it with air
			this.blocks = new short[Terrain.MAX_BLOCK_INDEX];
			Arrays.fill(this.blocks, (byte) Blocks.AIR_ID);
		} else {
			this.ensureBlockDecompressed();
		}

		// get the previous block in this location
		Block prevblock = Blocks.getBlockByID(this.blocks[index]);

		// unset the previous block
		prevblock.onUnset(this, x, y, z);

		// get the previous instance at this location, and remove it
		BlockInstance previnstance = this.removeBlockInstance(index);

		// if there was a block instance
		if (previnstance != null) {
			// unset callback for this instance
			previnstance.onUnset();
		}

		// set the new block
		this.blocks[index] = block.getID();

		// set callback
		block.onSet(this, x, y, z);

		// update number of block set
		if (prevblock.isVisible() && !block.isVisible()) {
			--this.visibleBlocks;
		} else if (!prevblock.isVisible() && block.isVisible()) {
			++this.visibleBlocks;
		}

		// get a new block instance for this new block
		BlockInstance instance = block.createBlockInstance(this, index);
		// if this block actually have (need) an instance
		if (instance != null) {

			// initialiaze the instance list if needed. (i.e if it is the first
			// block instance for this terrain)
			if (this.blockInstances == null) {
				this.blockInstances = new HashMap<Short, BlockInstance>();
			}
			// add the instance to the list
			this.blockInstances.put(index, instance);
			// instance set calback
			instance.onSet();
		}

		return (instance);
	}

	/** remove and return the block instance at the given location */
	private BlockInstance removeBlockInstance(Short index) {
		if (this.blockInstances == null) {
			return (null);
		}
		BlockInstance blockInstance = this.blockInstances.remove(index);

		// if there is no longer block instances, delete the hashmap
		if (this.blockInstances.size() == 0) {
			this.blockInstances = null;
		}
		return (blockInstance);
	}

	/**
	 * get the block instance at the given location (relative to the terrain)
	 */
	public BlockInstance getBlockInstanceAt(Short index) {
		if (this.blockInstances == null) {
			return (null);
		}
		return (this.blockInstances.get(index));
	}

	/**
	 * get the block instance at the given location (relative to the terrain)
	 */
	public BlockInstance getBlockInstance(int... xyz) {
		return (this.getBlockInstanceAt(this.getIndex(xyz)));
	}

	/**
	 * LIGHT BEGINS HERE:
	 * 
	 * THE IMPLEMENTATION IS BASED ON THIS WORK:
	 * https://www.seedofandromeda.com/blogs/29-fast-flood-fill-lighting-in-a-blocky-voxel-game-pt-1
	 * 
	 * SPECIAL THANK TO SOA TEAM FOR THEIR SHARING
	 */

	public class LightNodeAdd {
		public final Terrain terrain;
		public final short index;

		public LightNodeAdd(Terrain terrain, short index) {
			this.terrain = terrain;
			this.index = index;
		}
	}

	public class LightNodeRemoval {
		public final Terrain terrain;
		public final short index;
		public final byte value;

		public LightNodeRemoval(Terrain terrain, short index, byte value) {
			this.terrain = terrain;
			this.index = index;
			this.value = value;
		}
	}

	/** get the total normalized light value */
	public float getLight(int... xyz) {
		Terrain terrain = this.getRelativeTerrain(xyz);
		if (terrain == null) {
			return (0);
		}
		return (terrain.getLight(terrain.getIndex(xyz[0], xyz[1], xyz[2])));
	}

	/** get the total normalized light value */
	public float getLight(short index) {
		if (this.lights == null) {
			return (0.0F);
		}
		this.ensureLightDecompressed();
		byte sun = (byte) ((this.lights[index] >> 4) & 0xF);
		byte block = (byte) (this.lights[index] & 0xF);
		// return ((sun + block) / 32);
		return ((sun + block) / 16.0F);

	}

	/** get sunlight value */
	public final byte getSunlight(int... xyz) {

		Terrain terrain = this.getRelativeTerrain(xyz);
		if (terrain == null) {
			return (0);
		}
		return (terrain.getSunlight(terrain.getIndex(xyz[0], xyz[1], xyz[2])));
	}

	/** get sunlight value */
	public final byte getSunLight(short index) {
		if (this.lights == null) {
			return (0);
		}
		this.ensureLightDecompressed();
		return (byte) ((this.lights[index] >> 4) & 0xF);
	}

	/** set the sunlight value */
	public final void setSunLight(int x, int y, int z, byte val) {
		if (this.lights == null) {
			// initialize it, fill it with 0
			this.lights = new byte[Terrain.MAX_BLOCK_INDEX];
			Arrays.fill(this.lights, (byte) 0);
		}
		short index = this.getIndex(x, y, z);
		this.ensureLightDecompressed();
		this.lights[index] = (byte) ((this.lights[index] & 0xF) | (val << 4));
	}

	/** get the block light value */
	public final byte getBlockLight(int... xyz) {
		Terrain terrain = this.getRelativeTerrain(xyz);
		if (terrain == null) {
			return (0);
		}
		return (terrain.getBlockLight(terrain.getIndex(xyz[0], xyz[1], xyz[2])));
	}

	/** get the block light value */
	public byte getBlockLight(short index) {
		if (this.lights == null) {
			return (0);
		}
		this.ensureLightDecompressed();
		return ((byte) (this.lights[index] & 0xF));
	}

	/** set tje block light value */
	private final void setBlockLight(byte val, short index) {
		if (this.lights == null) {
			// initialize it, fill it with 0
			this.lights = new byte[Terrain.MAX_BLOCK_INDEX];
			Arrays.fill(this.lights, (byte) 0);
		}
		this.ensureLightDecompressed();
		this.lights[index] = (byte) ((this.lights[index] & 0xF0) | val);
	}

	public void addBlockLight(byte lightValue, int x, int y, int z) {
		this.addBlockLight(lightValue, this.getIndex(x, y, z));
	}

	/** add a light to the terrain */
	public void addBlockLight(byte lightValue, short index) {
		if (lightValue == 0) {
			return;
		}
		if (this.lightAddQueue == null) {
			this.lightAddQueue = new Stack<LightNodeAdd>();
		}
		this.lightAddQueue.add(new LightNodeAdd(this, index));
		this.setBlockLight(lightValue, index);
	}

	/** remove the light at given coordinates */
	public void removeLight(int x, int y, int z) {
		this.removeLight(this.getIndex(x, y, z));
	}

	public void removeLight(short index) {

		byte value = this.getBlockLight(index);
		if (value <= 0) {
			return;
		}

		if (this.lightRemovalQueue == null) {
			this.lightRemovalQueue = new Stack<LightNodeRemoval>();
		}

		this.lightRemovalQueue.push(new LightNodeRemoval(this, index, value));
		this.setBlockLight((byte) 0, index);
	}

	/** update the lighting */
	private void updateLights() {

		if (this.lightAddQueue.size() == 0 && this.lightRemovalQueue.size() == 0) {
			return;
		}

		// removing a light
		// the affected terrains
		ArrayList<Terrain> processedTerrains = new ArrayList<Terrain>(4);

		this.propagateLightRemovalQueue(processedTerrains);
		this.propagateLightAddQueue(processedTerrains);

		// update meshes
		for (Terrain terrain : processedTerrains) {
			terrain.requestMeshUpdate();
		}
	}

	private void propagateLightAddQueue(ArrayList<Terrain> processedTerrains) {
		// do the algorithm
		while (!this.lightAddQueue.isEmpty()) {

			// get the light value
			LightNodeAdd lightNode = this.lightAddQueue.pop();

			Terrain nodeTerrain = lightNode.terrain;

			if (!processedTerrains.contains(nodeTerrain)) {
				processedTerrains.add(nodeTerrain);
			}

			// get the index
			short index = lightNode.index;
			byte lightValue = nodeTerrain.getBlockLight(index);

			// next value
			byte nextLightValue = (byte) (lightValue - 1);

			// if reached the end; stop propagation
			if (nextLightValue == 0) {
				continue;
			}

			int z = nodeTerrain.getZFromIndex(index);
			int y = nodeTerrain.getYFromIndex(index, z);
			int x = nodeTerrain.getXFromIndex(index, y, z);

			// terrain world index
			int tx = nodeTerrain.terrainLocation.getWorldIndex().x;
			int ty = nodeTerrain.terrainLocation.getWorldIndex().y;
			int tz = nodeTerrain.terrainLocation.getWorldIndex().z;

			// propagate thought x negative
			if (x > 0) {
				nodeTerrain.floodFillLightAdd(nodeTerrain.getIndex(x - 1, y, z), nextLightValue);
			} else {
				Terrain terrain = nodeTerrain.getNeighbor(Face.FRONT);
				if (terrain == null) {
					terrain = this.getWorld().spawnTerrain(new Terrain(tx - 1, ty, tz));
				}
				if (terrain != null) {
					terrain.floodFillLightAdd(terrain.getIndex(Terrain.DIM - 1, y, z), nextLightValue);
				}
			}

			// propagate thought y negative
			if (y > 0) {
				nodeTerrain.floodFillLightAdd(nodeTerrain.getIndex(x, y - 1, z), nextLightValue);
			} else {
				Terrain terrain = nodeTerrain.getNeighbor(Face.BOT);
				if (terrain == null) {
					terrain = this.getWorld().spawnTerrain(new Terrain(tx, ty - 1, tz));
				}
				if (terrain != null) {
					terrain.floodFillLightAdd(terrain.getIndex(x, Terrain.DIM - 1, z), nextLightValue);
				}
			}

			// propagate thought z negative
			if (z > 0) {
				nodeTerrain.floodFillLightAdd(nodeTerrain.getIndex(x, y, z - 1), nextLightValue);
			} else {
				Terrain terrain = nodeTerrain.getNeighbor(Face.LEFT);
				if (terrain == null) {
					terrain = this.getWorld().spawnTerrain(new Terrain(tx, ty, tz - 1));
				}
				if (terrain != null) {
					terrain.floodFillLightAdd(terrain.getIndex(x, y, Terrain.DIM - 1), nextLightValue);
				}
			}

			// propagate thought x positive
			if (x < Terrain.DIM - 1) {
				nodeTerrain.floodFillLightAdd(nodeTerrain.getIndex(x + 1, y, z), nextLightValue);
			} else {
				Terrain terrain = nodeTerrain.getNeighbor(Face.BACK);
				if (terrain == null) {
					terrain = this.getWorld().spawnTerrain(new Terrain(tx + 1, ty, tz));
				}
				if (terrain != null) {
					terrain.floodFillLightAdd(terrain.getIndex(0, y, z), nextLightValue);
				}
			}

			// propagate thought y positive
			if (y < Terrain.DIM - 1) {
				nodeTerrain.floodFillLightAdd(nodeTerrain.getIndex(x, y + 1, z), nextLightValue);
			} else {
				Terrain terrain = nodeTerrain.getNeighbor(Face.TOP);
				if (terrain == null) {
					terrain = this.getWorld().spawnTerrain(new Terrain(tx, ty + 1, tz));
				}
				if (terrain != null) {
					terrain.floodFillLightAdd(terrain.getIndex(x, 0, z), nextLightValue);
				}
			}

			// propagate thought z positive
			if (z < Terrain.DIM - 1) {
				nodeTerrain.floodFillLightAdd(nodeTerrain.getIndex(x, y, z + 1), nextLightValue);
			} else {
				Terrain terrain = nodeTerrain.getNeighbor(Face.RIGHT);
				if (terrain == null) {
					terrain = this.getWorld().spawnTerrain(new Terrain(tx, ty, tz + 1));
				}
				if (terrain != null) {
					terrain.floodFillLightAdd(terrain.getIndex(x, y, 0), nextLightValue);
				}
			}
		}
	}

	private void floodFillLightAdd(short index, byte nextLightValue) {
		Block next = this.getBlockAt(index);
		if (!next.isOpaque()) {
			if (this.getBlockLight(index) < nextLightValue) {
				this.lightAddQueue.push(new LightNodeAdd(this, index));
				this.setBlockLight(nextLightValue, index);
			}
		}
	}

	private void propagateLightRemovalQueue(ArrayList<Terrain> processedTerrains) {

		// bfs algorithm
		int x, y, z;
		int tx, ty, tz;

		while (!this.lightRemovalQueue.isEmpty()) {

			// get the light value
			LightNodeRemoval lightValueNode = lightRemovalQueue.pop();
			Terrain nodeTerrain = lightValueNode.terrain;

			if (!processedTerrains.contains(nodeTerrain)) {
				processedTerrains.add(nodeTerrain);
			}

			// clear the light value
			short nodeIndex = lightValueNode.index;
			byte lightLevel = lightValueNode.value;

			z = nodeTerrain.getZFromIndex(nodeIndex);
			y = nodeTerrain.getYFromIndex(nodeIndex, z);
			x = nodeTerrain.getXFromIndex(nodeIndex, y, z);

			// terrain world index
			tx = nodeTerrain.terrainLocation.getWorldIndex().x;
			ty = nodeTerrain.terrainLocation.getWorldIndex().y;
			tz = nodeTerrain.terrainLocation.getWorldIndex().z;

			// propagate thought x negative
			if (x > 0) {
				nodeTerrain.floodFillLightRemove(nodeTerrain.getIndex(x - 1, y, z), lightLevel);
			} else {
				Terrain terrain = nodeTerrain.getNeighbor(Face.FRONT);
				if (terrain == null) {
					terrain = this.getWorld().spawnTerrain(new Terrain(tx - 1, ty, tz));
				}
				if (terrain != null) {
					terrain.floodFillLightRemove(terrain.getIndex(Terrain.DIM - 1, y, z), lightLevel);
				}
			}

			// propagate thought y negative
			if (y > 0) {
				nodeTerrain.floodFillLightRemove(nodeTerrain.getIndex(x, y - 1, z), lightLevel);
			} else {
				Terrain terrain = nodeTerrain.getNeighbor(Face.BOT);
				if (terrain == null) {
					terrain = this.getWorld().spawnTerrain(new Terrain(tx, ty - 1, tz));
				}
				if (terrain != null) {
					terrain.floodFillLightRemove(terrain.getIndex(x, Terrain.DIM - 1, z), lightLevel);
				}
			}

			// propagate thought z negative
			if (z > 0) {
				nodeTerrain.floodFillLightRemove(nodeTerrain.getIndex(x, y, z - 1), lightLevel);
			} else {
				Terrain terrain = nodeTerrain.getNeighbor(Face.LEFT);
				if (terrain == null) {
					terrain = this.getWorld().spawnTerrain(new Terrain(tx, ty, tz - 1));
				}
				if (terrain != null) {
					terrain.floodFillLightRemove(terrain.getIndex(x, y, Terrain.DIM - 1), lightLevel);
				}
			}

			// propagate thought x positive
			if (x < Terrain.DIM - 1) {
				nodeTerrain.floodFillLightRemove(nodeTerrain.getIndex(x + 1, y, z), lightLevel);
			} else {
				Terrain terrain = nodeTerrain.getNeighbor(Face.BACK);
				if (terrain == null) {
					terrain = this.getWorld().spawnTerrain(new Terrain(tx + 1, ty, tz));
				}
				if (terrain != null) {
					terrain.floodFillLightRemove(terrain.getIndex(0, y, z), lightLevel);
				}
			}

			// propagate thought x positive
			if (y < Terrain.DIM - 1) {
				nodeTerrain.floodFillLightRemove(nodeTerrain.getIndex(x, y + 1, z), lightLevel);
			} else {
				Terrain terrain = nodeTerrain.getNeighbor(Face.TOP);
				if (terrain == null) {
					terrain = this.getWorld().spawnTerrain(new Terrain(tx, ty + 1, tz));
				}
				if (terrain != null) {
					terrain.floodFillLightRemove(terrain.getIndex(x, 0, z), lightLevel);
				}
			}

			// propagate thought x positive
			if (z < Terrain.DIM - 1) {
				nodeTerrain.floodFillLightRemove(nodeTerrain.getIndex(x, y, z + 1), lightLevel);
			} else {
				Terrain terrain = nodeTerrain.getNeighbor(Face.RIGHT);
				if (terrain == null) {
					terrain = this.getWorld().spawnTerrain(new Terrain(tx, ty, tz + 1));
				}
				if (terrain != null) {
					terrain.floodFillLightRemove(terrain.getIndex(x, y, 0), lightLevel);
				}
			}
		}
	}

	private void floodFillLightRemove(short index, byte lightLevel) {
		byte neighborLevel = this.getBlockLight(index);
		if (neighborLevel != 0 && neighborLevel < lightLevel) {
			this.setBlockLight((byte) 0, index);
			this.lightRemovalQueue.push(new LightNodeRemoval(this, index, neighborLevel));
		} else if (neighborLevel >= lightLevel) {
			this.lightAddQueue.push(new LightNodeAdd(this, index));
		}
	}

	/** LIGHTS ENDS HERE */

	// @Override
	// public int hashCode() {
	// return (this.terrainLocation.hashCode() + this.world.hashCode());
	// }

	public void requestFaceVisibilityUpdate() {
		this.unsetState(Terrain.STATE_FACE_VISIBILTY_UP_TO_DATE);
	}

	/** return terrain location */
	public TerrainLocation getLocation() {
		return (this.terrainLocation);
	}

	public Vector3f getWorldPosition() {
		return (this.terrainLocation.getWorldPosition());
	}

	public Vector3i getWorldIndex() {
		return (this.terrainLocation.getWorldIndex());
	}

	@Override
	public String toString() {
		return ("Terrain: " + this.getLocation());
	}

	/**
	 * non overflow proof:
	 * 
	 * let D = Terrain.DIM
	 * 
	 * 0 <= x <= D - 1 0 <= y <= D - 1 0 <= z <= D - 1
	 *
	 * => 0 <= x + D * (y + z.D) <= (D - 1) + D . ((D - 1) + D . (D - 1)) => 0
	 * <= index <= (D - 1) + D . ((D - 1) + D^2 - D)) => 0 <= index <= (D - 1) +
	 * D . (D^2 - 1) => 0 <= index <= (D - 1) + D^3 - D) => 0 <= index <= D^3 -
	 * 1 => 0 <= index <= this.blocks.length : OK
	 *
	 * unicity proof: index = x + D * (y + D * z) = x + y.D + z.D^2 = z.D^2 +
	 * y.D + x
	 *
	 * if x < D, then x / D = 0 (we are doing division using integers). Then we
	 * know that:
	 *
	 * index / D = (z.D^2 + y.D + x) / D = z.D + y + x / D = z.D + y + 0 = z.D +
	 * y
	 *
	 * index / D^2 = (index / D) / D = (z.D + y) / D = z + y / D = z
	 *
	 * And so: y = index / D - z.D
	 *
	 * Finally: x = index - z.D^2 - y.D
	 *
	 * (x, y, z) are unique for a given index, and we found their value.
	 */
	public short getIndex(int x, int y, int z) {
		return (short) (x + Terrain.DIM * (y + Terrain.DIM * z));
	}

	public short getIndex(int... xyz) {
		return (this.getIndex(xyz[0], xyz[1], xyz[2]));
	}

	public int getXFromIndex(short index) {
		int z = this.getZFromIndex(index);
		return (this.getXFromIndex(index, this.getYFromIndex(index, z), z));
	}

	public int getXFromIndex(short index, int y, int z) {
		return (index - Terrain.DIM * (y + Terrain.DIM * z));
	}

	public int getYFromIndex(short index) {
		return (this.getYFromIndex(index, this.getZFromIndex(index)));
	}

	public int getYFromIndex(short index, int z) {
		return (index / Terrain.DIM - Terrain.DIM * z);
	}

	public int getZFromIndex(short index) {
		return (index / Terrain.DIM2);
	}

	public void getPosFromIndex(short index, int... xyz) {
		xyz[2] = this.getZFromIndex(index);
		xyz[1] = this.getYFromIndex(index, xyz[2]);
		xyz[0] = this.getXFromIndex(index, xyz[1], xyz[2]);
	}

	/**
	 * thoses functions return the left, right, top, bot, front or back terrain
	 * to this one
	 */
	public Terrain getNeighbor(int id) {
		Terrain neighbor = this.neighboors[id];
		if (neighbor != null) {
			return (neighbor);
		}

		if (this.world == null) {
			return (null);
		}

		Vector3i index = this.getLocation().getWorldIndex();
		int x = index.x + Face.get(id).getVector().x;
		int y = index.y + Face.get(id).getVector().y;
		int z = index.z + Face.get(id).getVector().z;
		return (this.world.getTerrain(x, y, z));
	}

	/** return terrain center world position */
	public Vector3f getCenter() {
		return (this.terrainLocation.getCenter());
	}

	public Terrain[] getNeighboors() {
		return (this.neighboors);
	}

	/** called when the terrain is added to the world */
	public final void onSpawned(World world) {
		this.setWorld(world);
		this.updateNeighboors();
		this.onGenerated();
		this.requestUpdateAll();
	}

	public void requestUpdateAll() {
		this.requestFaceVisibilityUpdate();
		this.requestMeshUpdate();
	}

	public void onGenerated() {

	}

	public void setWorld(World world) {
		this.world = world;
	}

	public World getWorld() {
		return (this.world);
	}

	/** update Neighboors terrain */
	public void updateNeighboors() {
		for (Face face : Face.values()) {
			Vector3i index = Vector3i.add(this.terrainLocation.getWorldIndex(), face.getVector());
			Terrain terrain = this.world.getTerrain(index);

			if (terrain != null) {
				terrain.setNeighboor(this, face.getOpposite());
			}
			this.setNeighboor(terrain, face);
		}
	}

	private void setNeighboor(Terrain terrain, Face face) {
		this.neighboors[face.getID()] = terrain;
	}

	/**
	 * return max height at given index for this terrain (relative to the
	 * terrain position)
	 */
	public int getHeightAt(int x, int z) {
		int y = 15;

		while (this.getBlockAt(x, y, z) == Blocks.AIR) {
			--y;
			if (y == -1) {
				return (-1);
			}
		}
		return (y);
	}

	/**
	 * this function updates the visibility of each face toward another using a
	 * flood fill algorythm for each cell which werent already visited: - use
	 * flood fill algorythm, and register which faces are touched by the flood -
	 * for each of touched faces, set the visibility linked with the others
	 * touched
	 */

	/**
	 * this set the 'this.facesVisibility' bits to 1 if faces can be seen from
	 * another
	 * 
	 * This uses an explicit stack (to avoid stackoverflow in recursive)
	 **/
	private void updateFaceVisiblity() {

		this.setState(STATE_FACE_VISIBILTY_UP_TO_DATE);

		if (this.facesVisibility == null) {
			this.facesVisibility = new boolean[6][6];
		}

		// reset visibility
		for (Face a : Face.faces) {
			for (Face b : Face.faces) {
				this.facesVisibility[a.getID()][b.getID()] = false;
			}
		}

		// virtual stack
		Stack<Vector3i> stack = new Stack<Vector3i>();
		short[][][] flood = new short[Terrain.DIM][Terrain.DIM][Terrain.DIM];
		short color = 1;
		boolean[] touched_by_flood = new boolean[6];

		for (int x = 0; x < Terrain.DIM; x++) {
			for (int y = 0; y < Terrain.DIM; y++) {
				for (int z = 0; z < Terrain.DIM; z++) {
					if (!this.getBlockAt(x, y, z).isOpaque() && flood[x][y][z] == 0) {
						for (int i = 0; i < 6; i++) {
							touched_by_flood[i] = false;
						}

						stack.push(new Vector3i(x, y, z));
						// this loop will empty the stack and propagate the
						// flood
						while (!stack.isEmpty()) {
							Vector3i pos = stack.pop();

							if (pos.x < 0) {
								touched_by_flood[Face.BACK] = true;
								continue;
							}

							if (pos.y < 0) {
								touched_by_flood[Face.BOT] = true;
								continue;
							}

							if (pos.z < 0) {
								touched_by_flood[Face.LEFT] = true;
								continue;
							}

							if (pos.x >= Terrain.DIM) {
								touched_by_flood[Face.FRONT] = true;
								continue;
							}

							if (pos.y >= Terrain.DIM) {
								touched_by_flood[Face.TOP] = true;
								continue;
							}

							if (pos.z >= Terrain.DIM) {
								touched_by_flood[Face.RIGHT] = true;
								continue;
							}

							if (this.getBlockAt(pos.x, pos.y, pos.z).isOpaque() || flood[pos.x][pos.y][pos.z] != 0) {
								// hitted a full block
								continue;
							}

							flood[pos.x][pos.y][pos.z] = color;

							stack.push(new Vector3i(pos.x + 1, pos.y + 0, pos.z + 0));
							stack.push(new Vector3i(pos.x - 1, pos.y + 0, pos.z + 0));
							stack.push(new Vector3i(pos.x + 0, pos.y + 1, pos.z + 0));
							stack.push(new Vector3i(pos.x + 0, pos.y - 1, pos.z + 0));
							stack.push(new Vector3i(pos.x + 0, pos.y + 0, pos.z + 1));
							stack.push(new Vector3i(pos.x + 0, pos.y + 0, pos.z - 1));
						}

						for (int i = 0; i < 6; i++) {
							if (touched_by_flood[i]) {
								for (int j = 0; j < 6; j++) {
									if (touched_by_flood[j]) {
										this.facesVisibility[i][j] = true;
										this.facesVisibility[j][i] = true;
									}
								}
							}
						}
					}
				}
			}
		}
	}

	/** return true if the given faces id can be seen from another */
	public boolean canFaceBeSeenFrom(int faceA, int faceB) {
		return (this.facesVisibility[faceA][faceB]);
	}

	public boolean canFaceBeSeenFrom(Face faceA, Face faceB) {
		return (this.canFaceBeSeenFrom(faceA.getID(), faceB.getID()));
	}

	public void destroy() {
		this.blocks = null;
		this.lights = null;
		this.visibleBlocks = 0;
		if (this.blockInstances != null) {
			this.blockInstances.clear();
			this.blockInstances = null;
		}
		this.facesVisibility = null;
	}

	/** request a rebuild of this terrain mesh */
	public void requestMeshUpdate() {
		this.unsetState(Terrain.STATE_VERTICES_UP_TO_DATE);
	}

	/** request a rebuild of this terrain neighboors meshes */
	public void requestNeighboorsMeshUpdate() {
		for (Terrain neighbor : this.getNeighboors()) {
			if (neighbor != null) {
				neighbor.requestMeshUpdate();
			}
		}
	}

	/** return raw block data */
	public short[] getRawBlocks() {
		return (this.blocks);
	}

	/** get the raw light map */
	public byte[] getRawLights() {
		return (this.lights);
	}

	/** get the number of visible blocks for this terrain */
	public int getVisibleBlocksCount() {
		return (this.visibleBlocks);
	}

}