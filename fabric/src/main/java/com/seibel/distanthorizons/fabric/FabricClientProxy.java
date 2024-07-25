/*
 *    This file is part of the Distant Horizons mod
 *    licensed under the GNU LGPL v3 License.
 *
 *    Copyright (C) 2020-2023 James Seibel
 *
 *    This program is free software: you can redistribute it and/or modify
 *    it under the terms of the GNU Lesser General Public License as published by
 *    the Free Software Foundation, version 3.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU Lesser General Public License for more details.
 *
 *    You should have received a copy of the GNU Lesser General Public License
 *    along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.seibel.distanthorizons.fabric;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.seibel.distanthorizons.common.AbstractModInitializer;
import com.seibel.distanthorizons.common.wrappers.McObjectConverter;
import com.seibel.distanthorizons.common.wrappers.world.ClientLevelWrapper;
import com.seibel.distanthorizons.core.api.internal.ClientApi;
import com.mojang.blaze3d.platform.InputConstants;
import com.seibel.distanthorizons.common.wrappers.chunk.ChunkWrapper;

import com.seibel.distanthorizons.core.api.internal.SharedApi;
import com.seibel.distanthorizons.core.dependencyInjection.ModAccessorInjector;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.util.threading.ThreadPoolUtil;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftClientWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.modAccessor.ISodiumAccessor;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IClientLevelWrapper;
import com.seibel.distanthorizons.core.util.math.Mat4f;
import com.seibel.distanthorizons.fabric.wrappers.modAccessor.SodiumAccessor;
//import io.netty.buffer.ByteBuf;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;

#if MC_VER < MC_1_19_4
import java.nio.FloatBuffer;
#endif
import java.util.HashSet;
import java.util.concurrent.ThreadPoolExecutor;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.phys.HitResult;
import org.apache.logging.log4j.Logger;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

/**
 * This handles all events sent to the client,
 * and is the starting point for most of the mod.
 * 
 * @author coolGi
 * @author Ran
 * @version 2023-7-27
 */
@Environment(EnvType.CLIENT)
public class FabricClientProxy implements AbstractModInitializer.IEventProxy
{
	private final ClientApi clientApi = ClientApi.INSTANCE;
	private static final IMinecraftClientWrapper MC = SingletonInjector.INSTANCE.get(IMinecraftClientWrapper.class);
	private static final Logger LOGGER = DhLoggerBuilder.getLogger();
	
	// TODO we shouldn't be filtering keys on the Forge/Fabric side, only in ClientApi
	private static final int[] KEY_TO_CHECK_FOR = { GLFW.GLFW_KEY_F6, GLFW.GLFW_KEY_F8, GLFW.GLFW_KEY_P};
	
	HashSet<Integer> previouslyPressKeyCodes = new HashSet<>();
	
	
	
	/**
	 * Registers Fabric Events
	 * @author Ran
	 */
	public void registerEvents()
	{
		LOGGER.info("Registering Fabric Client Events");
		
		
		//========================//
		// register mod accessors //
		//========================//
		
		SodiumAccessor sodiumAccessor = (SodiumAccessor) ModAccessorInjector.INSTANCE.get(ISodiumAccessor.class);
		
		
		
		//=============//
		// tick events //
		//=============//
		
		ClientTickEvents.START_CLIENT_TICK.register((client) -> ClientApi.INSTANCE.clientTickEvent());
		
		
		
		//==============//
		// chunk events //
		//==============//
		
		// ClientChunkLoadEvent
		ClientChunkEvents.CHUNK_LOAD.register((level, chunk) ->
		{
			IClientLevelWrapper wrappedLevel = ClientLevelWrapper.getWrapper(level);
			SharedApi.INSTANCE.chunkLoadEvent(new ChunkWrapper(chunk, level, wrappedLevel), wrappedLevel);
		});
		
		// (kinda) block break event
		// Since fabric doesn't have a client-side break-block API event, this is the next best thing
		AttackBlockCallback.EVENT.register((player, level, interactionHand, blockPos, direction) ->
		{
			// if we have access to the server, use the chunk save event instead 
			if (MC.clientConnectedToDedicatedServer())
			{
				if (SharedApi.isChunkAtBlockPosAlreadyUpdating(blockPos.getX(), blockPos.getZ()))
				{
					// executor to prevent locking up the render/event thread
					// if the getChunk() takes longer than expected 
					// (which can be caused by certain mods) 
					ThreadPoolExecutor executor = ThreadPoolUtil.getFileHandlerExecutor();
					if (executor != null)
					{
						executor.execute(() ->
						{
							ChunkAccess chunk = level.getChunk(blockPos);
							if (chunk != null)
							{
								//LOGGER.trace("attack block at blockPos: " + blockPos);
								
								IClientLevelWrapper wrappedLevel = ClientLevelWrapper.getWrapper((ClientLevel) level);
								SharedApi.INSTANCE.chunkBlockChangedEvent(
										new ChunkWrapper(chunk, level, wrappedLevel),
										wrappedLevel
								);
							}
						});
					}
				}
			}
			
			// don't stop the callback
			return InteractionResult.PASS;
		});
		
		// (kinda) block place event
		// Since fabric doesn't have a client-side place-block API event, this is the next best thing
		UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> 
		{
			// if we have access to the server, use the chunk save event instead 
			if (MC.clientConnectedToDedicatedServer())
			{
				if (hitResult.getType() == HitResult.Type.BLOCK
						&& !hitResult.isInside())
				{
					if (SharedApi.isChunkAtBlockPosAlreadyUpdating(hitResult.getBlockPos().getX(), hitResult.getBlockPos().getZ()))
					{
						// executor to prevent locking up the render/event thread
						// if the getChunk() takes longer than expected 
						// (which can be caused by certain mods) 
						ThreadPoolExecutor executor = ThreadPoolUtil.getFileHandlerExecutor();
						if (executor != null)
						{
							executor.execute(() ->
							{
								ChunkAccess chunk = level.getChunk(hitResult.getBlockPos());
								if (chunk != null)
								{
									//LOGGER.trace("use block at blockPos: " + hitResult.getBlockPos());
									
									IClientLevelWrapper wrappedLevel = ClientLevelWrapper.getWrapper((ClientLevel) level);
									SharedApi.INSTANCE.chunkBlockChangedEvent(
											new ChunkWrapper(chunk, level, wrappedLevel),
											wrappedLevel
									);
								}
							});
						}
					}
				}
			}
			
			// don't stop the callback
			return InteractionResult.PASS;
		});
		
		
		// Client Chunk Save
		ClientChunkEvents.CHUNK_UNLOAD.register((level, chunk) ->
		{
			IClientLevelWrapper wrappedLevel = ClientLevelWrapper.getWrapper(level);
			SharedApi.INSTANCE.chunkUnloadEvent(new ChunkWrapper(chunk, level, wrappedLevel), wrappedLevel);
		});
		
		
		
		//==============//
		// render event //
		//==============//

		WorldRenderEvents.AFTER_SETUP.register((renderContext) ->
		{
			Mat4f projectionMatrix = McObjectConverter.Convert(renderContext.projectionMatrix());
			
			Mat4f modelViewMatrix;
			#if MC_VER < MC_1_20_6
			modelViewMatrix = McObjectConverter.Convert(renderContext.matrixStack().last().pose());
			#else
			modelViewMatrix = McObjectConverter.Convert(renderContext.positionMatrix());
			#endif
			
			this.clientApi.renderLods(ClientLevelWrapper.getWrapper(renderContext.world()),
					modelViewMatrix,
					projectionMatrix,
					#if MC_VER < MC_1_21
					renderContext.tickDelta()
					#else
					renderContext.tickCounter().getGameTimeDeltaTicks()
					#endif
					);
		});

		// Debug keyboard event
		// FIXME: Use better hooks so it doesn't trigger key press events in text boxes
		ClientTickEvents.END_CLIENT_TICK.register(client -> 
		{
			if (client.player != null && !(Minecraft.getInstance().screen instanceof TitleScreen))
			{
				this.onKeyInput();
			}
		});
		
		
		
		//==================//
		// networking event //
		//==================//
		
//		ClientPlayNetworking.registerGlobalReceiver(new ResourceLocation(ModInfo.NETWORKING_RESOURCE_NAMESPACE, ModInfo.MULTIVERSE_PLUGIN_NAMESPACE),
//			(Minecraft client, ClientPacketListener handler, FriendlyByteBuf friendlyByteBuf, PacketSender responseSender) ->
//			{
//				// converting to a ByteBuf is necessary otherwise Fabric will complain when the game boots
//				ByteBuf nettyByteBuf = friendlyByteBuf.asByteBuf();
//				
//				// remove the Bukkit/Forge packet ID byte
//				nettyByteBuf.readByte();
//				
//				ClientApi.INSTANCE.serverMessageReceived(nettyByteBuf);
//			});
	}
	
	public void onKeyInput()
	{
		HashSet<Integer> currentKeyDown = new HashSet<>();
		
		// Note: Minecraft's InputConstants is same as GLFW Key values
		//TODO: Use mixin to hook directly into the GLFW Keyboard event in minecraft KeyboardHandler
		// Check all keys we need
		for (int keyCode = GLFW.GLFW_KEY_A; keyCode <= GLFW.GLFW_KEY_Z; keyCode++)
		{
			if (InputConstants.isKeyDown(Minecraft.getInstance().getWindow().getWindow(), keyCode))
			{
				currentKeyDown.add(keyCode);
			}
		}
		
		for (int keyCode : KEY_TO_CHECK_FOR)
		{
			if (InputConstants.isKeyDown(Minecraft.getInstance().getWindow().getWindow(), keyCode))
			{
				currentKeyDown.add(keyCode);
			}
		}
		
		// Diff and trigger events
		for (int keyCode : currentKeyDown)
		{
			if (!previouslyPressKeyCodes.contains(keyCode))
			{
				ClientApi.INSTANCE.keyPressedEvent(keyCode);
			}
		}
		
		// Update the set
		previouslyPressKeyCodes = currentKeyDown;
	}
	
}
