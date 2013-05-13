package me.nallar.tickthreading.minecraft.tickregion;

import java.util.LinkedHashSet;
import java.util.Set;

import me.nallar.tickthreading.minecraft.TickManager;
import me.nallar.tickthreading.util.TableFormatter;
import net.minecraft.world.World;

public abstract class TickRegion implements Runnable {
	final Object tickStateLock = new Object();
	volatile boolean ticking = false;
	final Set toRemove = new LinkedHashSet();
	final Set toAdd = new LinkedHashSet();
	final World world;
	final TickManager manager;
	public final int hashCode;
	private final int regionX;
	private final int regionZ;
	private float averageTickTime = 1;
	public boolean profilingEnabled = false;

	TickRegion(World world, TickManager manager, int regionX, int regionZ) {
		super();
		this.world = world;
		this.manager = manager;
		this.hashCode = TickManager.getHashCodeFromRegionCoords(regionX, regionZ);
		this.regionX = regionX;
		this.regionZ = regionZ;
	}

	public void die() {
	}

	@Override
	public void run() {
		if (shouldTick()) {
			long startTime = System.nanoTime();
			synchronized (tickStateLock) {
				ticking = true;
			}
			doTick();
			synchronized (tickStateLock) {
				ticking = false;
			}
			averageTickTime = ((averageTickTime * 127) + (System.nanoTime() - startTime)) / 128;
		}
	}

	boolean shouldTick() {
		return !manager.variableTickRate || averageTickTime < 55000000 || Math.random() < 55000000d / averageTickTime;
	}

	protected abstract void doTick();

	public float getAverageTickTime() {
		return averageTickTime / 1000000f;
	}

	public TableFormatter writeStats(TableFormatter tf) {
		return tf
				.row(this.getShortTypeName())
				.row(regionX * manager.regionSize)
				.row(regionZ * manager.regionSize)
				.row(size())
				.row(getAverageTickTime());
	}

	protected abstract String getShortTypeName();

	@Override
	public String toString() {
		return "rX: " + regionX + ", rZ: " + regionZ + ", hashCode: " + hashCode;
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof TickRegion)) {
			return false;
		}
		TickRegion otherTickRegion = (TickRegion) other;
		return otherTickRegion.hashCode == this.hashCode && this.getClass().isInstance(other);
	}

	public abstract boolean isEmpty();

	protected abstract int size();

	public abstract void processChanges();
}
