package com.seibel.distanthorizons.common.wrappers.level;

import com.seibel.distanthorizons.core.level.IServerKeyedClientLevel;
import com.seibel.distanthorizons.core.level.IKeyedClientLevelManager;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IClientLevelWrapper;
import net.minecraft.client.multiplayer.ClientLevel;
import org.jetbrains.annotations.Nullable;

public class KeyedClientLevelManager implements IKeyedClientLevelManager
{
	public static final KeyedClientLevelManager INSTANCE = new KeyedClientLevelManager();
	
	/** This is set and managed by the ClientApi for servers with support for DH. */
	@Nullable
	private IServerKeyedClientLevel serverKeyedLevel = null;
	
	
	
	//=============//
	// constructor //
	//=============//
	
	private KeyedClientLevelManager() { }
	
	
	
	//======================//
	// level override logic //
	//======================//
	
	@Override
	@Nullable
	public IServerKeyedClientLevel getServerKeyedLevel() { return this.serverKeyedLevel; }
	
	@Override
	public IServerKeyedClientLevel setServerKeyedLevel(IClientLevelWrapper clientLevel, String levelKey)
	{
		IServerKeyedClientLevel keyedLevel = new ServerKeyedClientLevel((ClientLevel) clientLevel.getWrappedMcObject(), levelKey);
		this.serverKeyedLevel = keyedLevel;
		return keyedLevel;
	}
	
	@Override
	public void clearKeyedLevel() { this.serverKeyedLevel = null; }
	@Override
	public boolean hasLevelSet() { return this.serverKeyedLevel != null; }
	
}
