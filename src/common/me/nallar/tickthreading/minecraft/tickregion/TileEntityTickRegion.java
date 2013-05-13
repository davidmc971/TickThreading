package me.nallar.tickthreading.minecraft.tickregion;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.locks.Lock;

import me.nallar.tickthreading.Log;
import me.nallar.tickthreading.minecraft.TickManager;
import me.nallar.tickthreading.minecraft.profiling.EntityTickProfiler;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkProviderServer;

public class TileEntityTickRegion extends TickRegion {
	private final Set<TileEntity> tileEntitySet = new LinkedHashSet<TileEntity>();

	public TileEntityTickRegion(World world, TickManager manager, int regionX, int regionY) {
		super(world, manager, regionX, regionY);
	}

	@Override
	public void doTick() {
		final ChunkProviderServer chunkProvider = (ChunkProviderServer) world.getChunkProvider();
		final World world = this.world;
		final boolean profilingEnabled = manager.profilingEnabled || this.profilingEnabled;
		Lock thisLock = null;
		Lock xPlusLock = null;
		Lock xMinusLock = null;
		Lock zPlusLock = null;
		Lock zMinusLock = null;
		EntityTickProfiler entityTickProfiler = null;
		long startTime = 0;
		if (profilingEnabled) {
			entityTickProfiler = manager.entityTickProfiler;
			if (this.profilingEnabled) {
				entityTickProfiler.tick();
			}
		}
		// This code is performance critical.
		// Locking calls are manipulated by the patcher,
		// INVOKEVIRTUAL java.util.concurrent.locks.Lock.lock/unlock() calls are replaced with
		// MONITORENTER/MONITOREXIT instructions.
		// For this reason, although we do use interfaces which correctly implement lock/unlock depending on the TileEntity,
		// it is critical that .lock() is not called on anything other than a NativeMutex instance in this code.
		// Behaving in this manner also allows us to avoid the overhead of calling an interface method.
		// Volatile reads are, if used only when necessary, fast. JVM will not optimize out repeat volatile reads, happens-before on field write.
		// Just don't read fields repeatedly unnecessary. Best not to do this with non-volatile fields anyway.
		final Iterator<TileEntity> tileEntitiesIterator = tileEntitySet.iterator();
		while (tileEntitiesIterator.hasNext()) {
			if (profilingEnabled) {
				startTime = System.nanoTime();
			}
			final TileEntity tileEntity = tileEntitiesIterator.next();
			final int xPos = tileEntity.xCoord;
			final int zPos = tileEntity.zCoord;
			if (tileEntity.lastTTX != xPos || tileEntity.lastTTY != tileEntity.yCoord || tileEntity.lastTTZ != zPos) {
				manager.lock(tileEntity);
				if (manager.getHashCode(xPos, zPos) != hashCode) {
					tileEntitiesIterator.remove();
					manager.add(tileEntity, false);
					if (hashCode != 0) {
						Log.fine("A tile entity is in the wrong TickRegion - was it moved by a player, or did something bug out?"
								+ "\n entity: " + Log.toString(tileEntity) + " at x,y,z:" + xPos + ',' + tileEntity.yCoord + ',' + zPos
								+ "\n Region: " + toString()
								+ "\n World: " + Log.name(tileEntity.worldObj));
					}
				}
				continue;
			}
			try {
				xPlusLock = tileEntity.xPlusLock;
				zPlusLock = tileEntity.zPlusLock;
				thisLock = tileEntity.thisLock;
				xMinusLock = tileEntity.xMinusLock;
				zMinusLock = tileEntity.zMinusLock;
				if (xPlusLock != null) {
					xPlusLock.lock();
				}
				if (zPlusLock != null) {
					zPlusLock.lock();
				}
				if (thisLock != null) {
					thisLock.lock();
				}
				if (zMinusLock != null) {
					zMinusLock.lock();
				}
				if (xMinusLock != null) {
					xMinusLock.lock();
				}
				if (tileEntity.isInvalid()) {
					tileEntitiesIterator.remove();
					manager.removed(tileEntity);
					//Log.fine("Removed tile entity: " + xPos + ", " + tileEntity.yCoord + ", " + zPos + "\ttype:" + tileEntity.getClass().toString());
					if (chunkProvider.chunkExists(xPos >> 4, zPos >> 4)) {
						Chunk chunk = world.getChunkFromChunkCoords(xPos >> 4, zPos >> 4);
						if (chunk != null) {
							chunk.cleanChunkBlockTileEntity(xPos, tileEntity.yCoord, zPos);
						}
					}
				} else if (tileEntity.worldObj != null && chunkProvider.chunkExists(xPos >> 4, zPos >> 4)) {
					tileEntity.tickTT();
				}
			} catch (Throwable throwable) {
				Log.severe("Exception ticking TileEntity " + Log.toString(tileEntity) + " at x,y,z:" + xPos + ',' + tileEntity.yCoord + ',' + zPos
						+ "\n World: " + Log.name(tileEntity.worldObj), throwable);
			} finally {
				if (xMinusLock != null) {
					xMinusLock.unlock();
				}
				if (zMinusLock != null) {
					zMinusLock.unlock();
				}
				if (thisLock != null) {
					thisLock.unlock();
				}
				if (zPlusLock != null) {
					zPlusLock.unlock();
				}
				if (xPlusLock != null) {
					xPlusLock.unlock();
				}

				if (profilingEnabled) {
					entityTickProfiler.record(tileEntity, System.nanoTime() - startTime);
				}
			}
		}
	}

	@Override
	protected String getShortTypeName() {
		return "T";
	}

	public boolean add(TileEntity tileEntity) {
		synchronized (tickStateLock) {
			if (ticking) {
				return toAdd.add(tileEntity) && !tileEntitySet.contains(tileEntity);
			} else {
				return tileEntitySet.add(tileEntity);
			}
		}
	}

	public boolean remove(TileEntity tileEntity) {
		synchronized (tickStateLock) {
			if (ticking) {
				return toRemove.add(tileEntity) && tileEntitySet.contains(tileEntity);
			} else {
				return tileEntitySet.remove(tileEntity);
			}
		}
	}

	@Override
	public void processChanges() {
		synchronized (tickStateLock) {
			if (ticking) {
				return;
			}
			tileEntitySet.addAll(toAdd);
			tileEntitySet.removeAll(toRemove);
			toAdd.clear();
			toRemove.clear();
		}
	}

	@Override
	public void die() {
		super.die();
		tileEntitySet.clear();
	}

	@Override
	public boolean isEmpty() {
		return tileEntitySet.isEmpty();
	}

	@Override
	public int size() {
		return tileEntitySet.size();
	}
}
