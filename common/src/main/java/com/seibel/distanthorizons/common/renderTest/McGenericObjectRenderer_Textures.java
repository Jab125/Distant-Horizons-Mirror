/*
 *    This file is part of the Distant Horizons mod
 *    licensed under the GNU LGPL v3 License.
 *
 *    Copyright (C) 2020 James Seibel
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

package com.seibel.distanthorizons.common.renderTest;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.platform.PolygonMode;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.seibel.distanthorizons.api.enums.rendering.EDhApiBlockMaterial;
import com.seibel.distanthorizons.api.interfaces.render.IDhApiCustomRenderRegister;
import com.seibel.distanthorizons.api.interfaces.render.IDhApiRenderableBoxGroup;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiBeforeGenericObjectRenderEvent;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiBeforeGenericRenderCleanupEvent;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiBeforeGenericRenderSetupEvent;
import com.seibel.distanthorizons.api.objects.math.DhApiVec3d;
import com.seibel.distanthorizons.api.objects.render.DhApiRenderableBox;
import com.seibel.distanthorizons.api.objects.render.DhApiRenderableBoxGroupShading;
import com.seibel.distanthorizons.common.wrappers.misc.LightMapWrapper;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.logging.DhLogger;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.logging.f3.F3Screen;
import com.seibel.distanthorizons.core.render.renderer.RenderParams;
import com.seibel.distanthorizons.core.render.renderer.generic.GenericRenderObjectFactory;
import com.seibel.distanthorizons.core.render.renderer.generic.InstancedVboContainer;
import com.seibel.distanthorizons.core.render.renderer.generic.RenderableBoxGroup;
import com.seibel.distanthorizons.core.util.LodUtil;
import com.seibel.distanthorizons.core.util.math.Mat4f;
import com.seibel.distanthorizons.core.util.math.Vec3d;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftGLWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftRenderWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IProfilerWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.render.IMcGenericRenderer;
import com.seibel.distanthorizons.coreapi.DependencyInjection.ApiEventInjector;
import com.seibel.distanthorizons.coreapi.ModInfo;
import net.minecraft.resources.Identifier;
import org.lwjgl.system.MemoryUtil;

import java.awt.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Handles rendering generic groups of {@link DhApiRenderableBox}.
 * 
 * @see IDhApiCustomRenderRegister
 * @see DhApiRenderableBox
 */
public class McGenericObjectRenderer_Textures implements IMcGenericRenderer
{
	private static final DhLogger LOGGER = new DhLoggerBuilder().build();
	
	private static final IMinecraftRenderWrapper MC_RENDER = SingletonInjector.INSTANCE.get(IMinecraftRenderWrapper.class);
	private static final IMinecraftGLWrapper GLMC = SingletonInjector.INSTANCE.get(IMinecraftGLWrapper.class);
	
	private static final DhApiRenderableBoxGroupShading DEFAULT_SHADING = DhApiRenderableBoxGroupShading.getUnshaded();
	
	/** 
	 * Can be used to troubleshoot the renderer. 
	 * If enabled several debug objects will render around (0,150,0). 
	 */
	public static final boolean RENDER_DEBUG_OBJECTS = false;
	
	/** A box from 0,0,0 to 1,1,1 */
	private static final float[] BOX_VERTICES = {
		//region
		// Pos x y z
		
		// min X, vertical face
		0, 0, 0,
		1, 0, 0,
		1, 1, 0,
		0, 1, 0,
		// max X, vertical face
		0, 1, 1,
		1, 1, 1,
		1, 0, 1,
		0, 0, 1,
		
		// min Z, vertical face
		0, 0, 1,
		0, 0, 0,
		0, 1, 0,
		0, 1, 1,
		// max Z, vertical face
		1, 0, 1,
		1, 1, 1,
		1, 1, 0,
		1, 0, 0,
		
		// min Y, horizontal face
		0, 0, 1,
		1, 0, 1,
		1, 0, 0,
		0, 0, 0,
		// max Y, horizontal face
		0, 1, 1,
		1, 1, 1,
		1, 1, 0,
		0, 1, 0,
		//endregion
	};
	
	private static final int[] BOX_INDICES = {
		//region
		// min X, vertical face
		2, 1, 0,
		0, 3, 2,
		// max X, vertical face
		6, 5, 4,
		4, 7, 6,
		
		// min Z, vertical face
		10, 9, 8,
		8, 11, 10,
		// max Z, vertical face
		14, 13, 12,
		12, 15, 14,
		
		// min Y, horizontal face
		18, 17, 16,
		16, 19, 18,
		// max Y, horizontal face
		20, 21, 22,
		22, 23, 20,
		//endregion
	};
	
	private final ConcurrentHashMap<Long, RenderableBoxGroup> boxGroupById = new ConcurrentHashMap<>();
	
	
	// rendering setup
	private boolean init = false;
	
	private VertexFormat vertexFormat;
	
	private RenderPipeline opaquePipeline;
	private RenderPipeline transparentPipeline;
	
	private GpuBuffer boxVertexBuffer;
	private GpuBuffer boxIndexBuffer;
	
	private GpuBuffer fragUniformBuffer;
	private GpuBuffer vertUniformBuffer;
	
	
	
	//=============//
	// constructor //
	//=============//
	//region
	
	public McGenericObjectRenderer_Textures() { }
	
	public void init()
	{
		if (this.init)
		{
			return;
		}
		this.init = true;
		
		this.vertexFormat = VertexFormat.builder()
			.add("vPosition", DhVertexFormat.FLOAT_XYZ_POS)
			.build();
		
		this.createPipelines();
		this.createBuffers();
		
		if (RENDER_DEBUG_OBJECTS)
		{
			this.addGenericDebugObjects();
		}
	}
	private void createPipelines()
	{
		GpuDevice gpuDevice = RenderSystem.getDevice();
		CommandEncoder commandEncoder = gpuDevice.createCommandEncoder();
		
		
		RenderPipeline.Builder pipelineBuilder = RenderPipeline.builder();
		{
			pipelineBuilder.withCull(true);
			pipelineBuilder.withDepthWrite(true);
			pipelineBuilder.withDepthTestFunction(DepthTestFunction.LESS_DEPTH_TEST);
			pipelineBuilder.withColorWrite(true);
			pipelineBuilder.withPolygonMode(PolygonMode.FILL);
			pipelineBuilder.withLocation(Identifier.parse("distanthorizons:generic"));
			
			pipelineBuilder.withVertexShader(Identifier.fromNamespaceAndPath("distanthorizons", "generic/vert"));
			pipelineBuilder.withFragmentShader(Identifier.fromNamespaceAndPath("distanthorizons", "generic/frag"));
			
			pipelineBuilder.withSampler("uColorMap");
			
			pipelineBuilder.withSampler("uLightMap");
			
			pipelineBuilder.withUniform("vertUniformBlock", UniformType.UNIFORM_BUFFER);
			
			pipelineBuilder.withVertexFormat(this.vertexFormat, VertexFormat.Mode.TRIANGLES);
		}
		
		// opaque
		{
			pipelineBuilder.withoutBlend();
			this.opaquePipeline = pipelineBuilder.build();
		}
		
		// transparent
		{
			pipelineBuilder.withBlend(BlendFunction.TRANSLUCENT);
			this.transparentPipeline = pipelineBuilder.build();
		}
		
	}
	private void createBuffers()
	{
		// box vertices 
		ByteBuffer boxVerticesBuffer = MemoryUtil.memAlloc(BOX_VERTICES.length * Float.BYTES);
		boxVerticesBuffer.asFloatBuffer().put(BOX_VERTICES);
		boxVerticesBuffer.rewind();
		MemoryUtil.memFree(boxVerticesBuffer);
		
		
		GpuDevice gpuDevice = RenderSystem.getDevice();
		CommandEncoder commandEncoder = gpuDevice.createCommandEncoder();
		
		// upload vertex data
		{
			Supplier<String> labelSupplier = () -> "distantHorizons:McGenericRenderer";
			int usage = 8 | 32; // is this just using OpenGL VBO flags?, if so I can't find it, supposedly GlDevice on Mojang's side
			int size = BOX_VERTICES.length * Float.BYTES;
			this.boxVertexBuffer = gpuDevice.createBuffer(labelSupplier, usage, size);
			
			{
				int offset = 0;
				int length = BOX_VERTICES.length * Float.BYTES;
				GpuBufferSlice bufferSlice = new GpuBufferSlice(this.boxVertexBuffer, offset, length);
				
				ByteBuffer byteBuffer = ByteBuffer.allocateDirect(BOX_VERTICES.length * Float.BYTES);
				// Fill buffer with vertices.
				byteBuffer.order(ByteOrder.nativeOrder());
				byteBuffer.asFloatBuffer().put(BOX_VERTICES);
				byteBuffer.rewind();
				
				commandEncoder.writeToBuffer(bufferSlice, byteBuffer);
			}
		}
		
		// box vertex indexes
		{
			ByteBuffer buffer = ByteBuffer.allocateDirect(BOX_INDICES.length * Integer.BYTES);
			buffer.asIntBuffer().put(BOX_INDICES);
			buffer.rewind();
			
			
			// TODO
			// GpuBuffer.USAGE_UNIFORM = 128
			// GpuBuffer.USAGE_INDEX = 64
			int usage = 8 | 32 | 64 | 128; // is this just using OpenGL VBO flags?, if so I can't find it, supposedly GlDevice on Mojang's side
			this.boxIndexBuffer = gpuDevice.createBuffer(() -> "DH Generic Index Buffer", usage, buffer.capacity());
			
			int offset = 0;
			GpuBufferSlice bufferSlice = new GpuBufferSlice(this.boxIndexBuffer, offset, buffer.capacity());
			commandEncoder.writeToBuffer(bufferSlice, buffer);
		}
	}
	private void addGenericDebugObjects()
	{
		GenericRenderObjectFactory factory = GenericRenderObjectFactory.INSTANCE;
		
		
		// single giant box
		IDhApiRenderableBoxGroup singleGiantBoxGroup = factory.createForSingleBox(
				ModInfo.NAME + ":CyanChunkBox",
				new DhApiRenderableBox(
						new DhApiVec3d(0,0,0), new DhApiVec3d(16,190,16),
						new Color(Color.CYAN.getRed(), Color.CYAN.getGreen(), Color.CYAN.getBlue(), 125),
						EDhApiBlockMaterial.WATER)
		);
		singleGiantBoxGroup.setSkyLight(LodUtil.MAX_MC_LIGHT);
		singleGiantBoxGroup.setBlockLight(LodUtil.MAX_MC_LIGHT);
		this.add(singleGiantBoxGroup);


		// single slender box
		IDhApiRenderableBoxGroup singleTallBoxGroup = factory.createForSingleBox(
				ModInfo.NAME + ":GreenBeacon",
				new DhApiRenderableBox(
						new DhApiVec3d(16,0,31), new DhApiVec3d(17,2000,32),
						new Color(Color.GREEN.getRed(), Color.GREEN.getGreen(), Color.GREEN.getBlue(), 125),
						EDhApiBlockMaterial.ILLUMINATED)
		);
		singleTallBoxGroup.setSkyLight(LodUtil.MAX_MC_LIGHT);
		singleTallBoxGroup.setBlockLight(LodUtil.MAX_MC_LIGHT);
		this.add(singleTallBoxGroup);


		// absolute box group
		ArrayList<DhApiRenderableBox> absBoxList = new ArrayList<>();
		for (int i = 0; i < 18; i++)
		{
			absBoxList.add(new DhApiRenderableBox(
					new DhApiVec3d(i,150+i,24), new DhApiVec3d(1+i,151+i,25),
					new Color(Color.ORANGE.getRed(), Color.ORANGE.getGreen(), Color.ORANGE.getBlue()),
					EDhApiBlockMaterial.LAVA
				)
			);
		}
		IDhApiRenderableBoxGroup absolutePosBoxGroup = factory.createAbsolutePositionedGroup(ModInfo.NAME + ":OrangeStairs", absBoxList);
		this.add(absolutePosBoxGroup);


		// relative box group
		ArrayList<DhApiRenderableBox> relBoxList = new ArrayList<>();
		for (int i = 0; i < 8; i+=2)
		{
			relBoxList.add(new DhApiRenderableBox(
					new DhApiVec3d(0,i,0), new DhApiVec3d(1,1+i,1),
					new Color(Color.MAGENTA.getRed(), Color.MAGENTA.getGreen(), Color.MAGENTA.getBlue()),
					EDhApiBlockMaterial.METAL
				)
			);
		}
		IDhApiRenderableBoxGroup relativePosBoxGroup = factory.createRelativePositionedGroup(
				ModInfo.NAME + ":MovingMagentaGroup",
				new DhApiVec3d(24, 140, 24),
				relBoxList);
		relativePosBoxGroup.setPreRenderFunc((event) ->
		{
			DhApiVec3d pos = relativePosBoxGroup.getOriginBlockPos();
			pos.x += event.partialTicks / 2;
			pos.x %= 32;
			relativePosBoxGroup.setOriginBlockPos(pos);
		});
		this.add(relativePosBoxGroup);


		// massive relative box group
		ArrayList<DhApiRenderableBox> massRelBoxList = new ArrayList<>();
		for (int x = 0; x < 50*2; x+=2)
		{
			for (int z = 0; z < 50*2; z+=2)
			{
				massRelBoxList.add(new DhApiRenderableBox(
						new DhApiVec3d(-x, 0, -z), new DhApiVec3d(1-x, 1, 1-z),
						new Color(Color.RED.getRed(), Color.RED.getGreen(), Color.RED.getBlue()),
						EDhApiBlockMaterial.TERRACOTTA
					)
				);
			}
		}
		IDhApiRenderableBoxGroup massRelativePosBoxGroup = factory.createRelativePositionedGroup(
				ModInfo.NAME + ":MassRedGroup",
				new DhApiVec3d(-25, 140, 0),
				massRelBoxList);
		massRelativePosBoxGroup.setPreRenderFunc((event) ->
		{
			DhApiVec3d blockPos = massRelativePosBoxGroup.getOriginBlockPos();
			blockPos.y += event.partialTicks / 4;
			if (blockPos.y > 150f)
			{
				blockPos.y = 140f;

				Color newColor = (massRelativePosBoxGroup.get(0).color == Color.RED) ? Color.RED.darker() : Color.RED;
				massRelativePosBoxGroup.forEach((box) -> { box.color = newColor; });
				massRelativePosBoxGroup.triggerBoxChange();
			}

			massRelativePosBoxGroup.setOriginBlockPos(blockPos);
		});
		this.add(massRelativePosBoxGroup);
	}
	
	//endregion
	
	
	
	//==============//
	// registration //
	//==============//
	//region
	
	@Override
	public void add(IDhApiRenderableBoxGroup iBoxGroup) throws IllegalArgumentException 
	{
		if (!(iBoxGroup instanceof RenderableBoxGroup))
		{
			throw new IllegalArgumentException("Box group must be of type ["+ RenderableBoxGroup.class.getSimpleName()+"], type received: ["+(iBoxGroup != null ? iBoxGroup.getClass() : "NULL")+"].");
		}
		RenderableBoxGroup boxGroup = (RenderableBoxGroup) iBoxGroup;
		
		
		long id = boxGroup.getId();
		if (this.boxGroupById.containsKey(id))
		{
			throw new IllegalArgumentException("A box group with the ID [" + id + "] is already present.");
		}
		
		this.boxGroupById.put(id, boxGroup);
	}
	
	@Override
	public IDhApiRenderableBoxGroup remove(long id) { return this.boxGroupById.remove(id); }
	
	public void clear() { this.boxGroupById.clear(); }
	
	//endregion
	
	
	
	//===========//
	// rendering //
	//===========//
	//region
	
	/**
	 * @param renderingWithSsao 
	 *      if true that means this render call is happening before the SSAO pass
     *      and any objects rendered in this pass will have SSAO applied to them.
	 */
	@Override
	public void render(RenderParams renderEventParam, IProfilerWrapper profiler, boolean renderingWithSsao)
	{
		//==============//
		// render setup //
		//==============//
		//#region
		
		profiler.push("setup");
		
		this.init();
		
		ApiEventInjector.INSTANCE.fireAllEvents(DhApiBeforeGenericRenderSetupEvent.class, renderEventParam);
		
		Vec3d camPos = MC_RENDER.getCameraExactPosition();
		
		//#endregion
		
		if (McLodRenderer.INSTANCE.dhColorTexture == null
			|| McLodRenderer.INSTANCE.dhDepthTexture == null)
		{
			return;
		}
		
		
		
		//===========//
		// rendering //
		//===========//
		//#region
		
		GpuDevice gpuDevice = RenderSystem.getDevice();
		CommandEncoder commandEncoder = gpuDevice.createCommandEncoder();
		
		Collection<RenderableBoxGroup> boxList = this.boxGroupById.values();
		for (RenderableBoxGroup boxGroup : boxList)
		{
			// validation //
			
			// shouldn't happen, but just in case
			if (boxGroup == null)
			{
				continue;
			}
			
			// skip boxes that shouldn't render this pass
			if (boxGroup.ssaoEnabled != renderingWithSsao)
			{
				continue;
			}
			
			profiler.popPush("render prep");
			boxGroup.preRender(renderEventParam); // called even if the group is inactive, so the group can be activate if desired
			
			// ignore inactive groups
			if (!boxGroup.active)
			{
				continue;
			}
			
			// allow API users to cancel this object's rendering
			boolean cancelRendering = ApiEventInjector.INSTANCE.fireAllEvents(DhApiBeforeGenericObjectRenderEvent.class, new DhApiBeforeGenericObjectRenderEvent.EventParam(renderEventParam, boxGroup));
			if (cancelRendering)
			{
				continue;
			}
			
			// update instanced data if needed
			{
				boxGroup.tryUpdateInstancedDataAsync();
				
				// skip groups that haven't been uploaded yet
				if (boxGroup.instancedVbos.getState() != InstancedVboContainer.EState.RENDER)
				{
					continue;
				}
			}
			
			
			DhApiRenderableBoxGroupShading shading = boxGroup.shading;
			if (shading == null)
			{
				shading = DEFAULT_SHADING;
			}
			
			// uniforms
			{
				int uniformBufferSize = new Std140SizeCalculator()
					.putIVec3() // uOffsetChunk
					.putVec3() // uOffsetSubChunk
					.putIVec3() // uCameraPosChunk
					.putVec3() // uCameraPosSubChunk
					
					.putMat4f() // uProjectionMvm
					.putInt() // uSkyLight
					.putInt() // uBlockLight
					
					.putFloat() // uNorthShading
					.putFloat() // uSouthShading
					.putFloat() // uEastShading
					.putFloat() // uWestShading
					.putFloat() // uTopShading
					.putFloat() // uBottomShading
					.get();
				
				
				// create data //
				
				Mat4f projectionMvmMatrix = new Mat4f(renderEventParam.dhProjectionMatrix);
				projectionMvmMatrix.multiply(renderEventParam.dhModelViewMatrix);
				
				
				// upload data //
				
				ByteBuffer buffer = ByteBuffer.allocateDirect(uniformBufferSize);
				buffer.order(ByteOrder.LITTLE_ENDIAN);
				buffer = Std140Builder.intoBuffer(buffer)
					.putIVec3(
						LodUtil.getChunkPosFromDouble(boxGroup.getOriginBlockPos().x),
						LodUtil.getChunkPosFromDouble(boxGroup.getOriginBlockPos().y),
						LodUtil.getChunkPosFromDouble(boxGroup.getOriginBlockPos().z)
					) // uOffsetChunk
					.putVec3(
						LodUtil.getSubChunkPosFromDouble(boxGroup.getOriginBlockPos().x),
						LodUtil.getSubChunkPosFromDouble(boxGroup.getOriginBlockPos().y),
						LodUtil.getSubChunkPosFromDouble(boxGroup.getOriginBlockPos().z)
					) // uOffsetSubChunk
					.putIVec3(
						LodUtil.getChunkPosFromDouble(camPos.x),
						LodUtil.getChunkPosFromDouble(camPos.y),
						LodUtil.getChunkPosFromDouble(camPos.z)
					) // uCameraPosChunk
					.putVec3(
						LodUtil.getSubChunkPosFromDouble(camPos.x),
						LodUtil.getSubChunkPosFromDouble(camPos.y),
						LodUtil.getSubChunkPosFromDouble(camPos.z)
					) // uCameraPosSubChunk
					
					.putMat4f(projectionMvmMatrix.createJomlMatrix()) // uProjectionMvm
					.putInt(boxGroup.getSkyLight()) // uSkyLight
					.putInt(boxGroup.getBlockLight()) // uBlockLight
					
					.putFloat(shading.north)
					.putFloat(shading.south)
					.putFloat(shading.east)
					.putFloat(shading.west)
					.putFloat(shading.top)
					.putFloat(shading.bottom)
					
					.get()
				;
				
				this.fragUniformBuffer = UniformHandler.createBuffer("vertUniformBlock", uniformBufferSize, this.fragUniformBuffer);
				GpuBufferSlice bufferSlice = new GpuBufferSlice(this.fragUniformBuffer, 0, uniformBufferSize);
				
				commandEncoder.writeToBuffer(bufferSlice, buffer);
			}
			
			
			
			
			// render //
			
			profiler.popPush("rendering");
			profiler.push(boxGroup.getResourceLocationNamespace());
			profiler.push(boxGroup.getResourceLocationPath());
			
			Supplier<String> debugLabelSupplier = () -> "distantHorizons:McTestRenderer";
			GpuTextureView colorTexture = gpuDevice.createTextureView(McLodRenderer.INSTANCE.dhColorTexture);
			OptionalInt optionalClearColorAsInt = OptionalInt.empty();
			GpuTextureView depthTexture = gpuDevice.createTextureView(McLodRenderer.INSTANCE.dhDepthTexture);
			OptionalDouble optionalDepthValueAsDouble = OptionalDouble.empty();
			
			try (RenderPass renderPass = commandEncoder.createRenderPass(
				debugLabelSupplier,
				colorTexture,
				optionalClearColorAsInt,
				depthTexture, optionalDepthValueAsDouble))
			{
				this.renderBoxGroupInstanced(renderPass, renderEventParam, boxGroup, camPos, profiler);
			}
			
			profiler.pop(); // resource path
			profiler.pop(); // resource namespace
			
			boxGroup.postRender(renderEventParam);
		}
		
		//#endregion
		
		
		
		//==========//
		// clean up //
		//==========//
		//region
		
		profiler.popPush("cleanup");
		
		ApiEventInjector.INSTANCE.fireAllEvents(DhApiBeforeGenericRenderCleanupEvent.class, renderEventParam);
		
		profiler.pop();
		
		//endregion
	}
	
	//endregion
	
	
	
	//=====================//
	// instanced rendering //
	//=====================//
	//region
	
	private void renderBoxGroupInstanced(
		RenderPass renderPass, RenderParams renderEventParam, 
		RenderableBoxGroup boxGroup, Vec3d camPos,
		IProfilerWrapper profiler)
	{
		// update instance data //
		
		profiler.push("vertex setup");
		
		GpuDevice gpuDevice = RenderSystem.getDevice();
		
		McInstancedVboContainer_Textures container = (McInstancedVboContainer_Textures) boxGroup.instancedVbos;
		
		
		// bind MC Lightmap
		{
			LightMapWrapper lightMapWrapper = (LightMapWrapper) renderEventParam.lightmap;
			
			GpuTextureView textureView = gpuDevice.createTextureView(lightMapWrapper.gpuTexture);
			GpuSampler gpuSampler = gpuDevice.createSampler(
				AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE, // U,V
				FilterMode.NEAREST, FilterMode.NEAREST, // minFilter, magFilter
				1, // maxAnisotropy 
				OptionalDouble.empty() // maxLod
			);
			renderPass.bindTexture("uLightMap", textureView, gpuSampler);
		}
		
		renderPass.setIndexBuffer(this.boxIndexBuffer, VertexFormat.IndexType.INT);
		
		
		
		// Bind instance data //
		profiler.popPush("binding");
		{
			GpuTextureView colorView = gpuDevice.createTextureView(container.colorTexture);
			GpuSampler colorSampler = gpuDevice.createSampler(
				AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE, // U,V
				FilterMode.NEAREST, FilterMode.NEAREST, // minFilter, magFilter
				1, // maxAnisotropy 
				OptionalDouble.empty() // maxLod
			);
			renderPass.bindTexture("uColorMap", colorView, colorSampler);
			
			
			renderPass.setVertexBuffer(0, this.boxVertexBuffer);
		}
		
		renderPass.setUniform("vertUniformBlock", this.fragUniformBuffer);
		
		// set pipeline
		renderPass.setPipeline(this.opaquePipeline); // TODO
		
		// Draw instanced
		profiler.popPush("render");
		if (container.uploadedBoxCount > 0)
		{
			renderPass.drawIndexed(
				/*indexStart*/ 0,
				/*firstIndex*/0,
				/*indexCount*/BOX_INDICES.length,
				/*instanceCount*/container.uploadedBoxCount);
			
		}
		
		profiler.pop();
	}
	
	//endregion
	
	
	
	//=========//
	// F3 menu //
	//=========//
	//region
	
	public String getVboRenderDebugMenuString()
	{
		// get counts
		int totalGroupCount = this.boxGroupById.size();
		int totalBoxCount = 0;
		
		int activeGroupCount = 0;
		int activeBoxCount = 0;
		
		for (long key : this.boxGroupById.keySet())
		{
			RenderableBoxGroup renderGroup = this.boxGroupById.get(key);
			if (renderGroup.active)
			{
				activeGroupCount++;
				activeBoxCount += renderGroup.size();
			}
			totalBoxCount += renderGroup.size();
		}
		
		
		return "Generic Obj #: " + F3Screen.NUMBER_FORMAT.format(activeGroupCount) + "/" + F3Screen.NUMBER_FORMAT.format(totalGroupCount) + ", " +
				"Cube #: " + F3Screen.NUMBER_FORMAT.format(activeBoxCount) + "/" + F3Screen.NUMBER_FORMAT.format(totalBoxCount);
	}
	
	//endregion
	
	
	
}
