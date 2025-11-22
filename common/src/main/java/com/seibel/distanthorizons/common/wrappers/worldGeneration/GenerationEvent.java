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

package com.seibel.distanthorizons.common.wrappers.worldGeneration;

import java.util.concurrent.*;
import java.util.function.Consumer;

import com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiDistantGeneratorMode;
import com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiWorldGenerationStep;
import com.seibel.distanthorizons.core.util.ExceptionUtil;
import com.seibel.distanthorizons.core.util.objects.UncheckedInterruptedException;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.pos.DhChunkPos;
import com.seibel.distanthorizons.core.util.objects.EventTimer;
import com.seibel.distanthorizons.core.util.threading.ThreadPoolUtil;
import com.seibel.distanthorizons.core.wrapperInterfaces.chunk.IChunkWrapper;

import com.seibel.distanthorizons.core.logging.DhLogger;

public final class GenerationEvent
{
	private static final DhLogger LOGGER = new DhLoggerBuilder().build();;
	
	private static int generationFutureDebugIDs = 0; // TODO make atomic int?
	
	
	public final int id;
	public final ThreadWorldGenParams threadedParam;
	public final DhChunkPos minPos;
	/** the number of chunks wide this event is */
	public final int widthInChunks;
	public final EDhApiWorldGenerationStep targetGenerationStep;
	public final EDhApiDistantGeneratorMode generatorMode;
	public EventTimer timer = null;
	public long inQueueTime;
	public long timeoutTime = -1;
	public final CompletableFuture<Void> future = new CompletableFuture<>();
	public final Consumer<IChunkWrapper> resultConsumer;
	
	
	
	//=============//
	// constructor //
	//=============//
	
	public GenerationEvent(
			DhChunkPos minPos, int widthInChunks, BatchGenerationEnvironment generationGroup,
			EDhApiDistantGeneratorMode generatorMode, EDhApiWorldGenerationStep targetGenerationStep, Consumer<IChunkWrapper> resultConsumer)
	{
		this.inQueueTime = System.nanoTime();
		this.id = generationFutureDebugIDs++;
		this.minPos = minPos;
		this.widthInChunks = widthInChunks;
		this.generatorMode = generatorMode;
		this.targetGenerationStep = targetGenerationStep;
		this.threadedParam = ThreadWorldGenParams.getOrMake(generationGroup.params);
		this.resultConsumer = resultConsumer;
	}
	
	
	
	public static GenerationEvent startEvent(
			DhChunkPos minPos, int size, BatchGenerationEnvironment genEnvironment,
			EDhApiDistantGeneratorMode generatorMode, EDhApiWorldGenerationStep target, Consumer<IChunkWrapper> resultConsumer,
			ExecutorService worldGeneratorThreadPool)
	{
		GenerationEvent generationEvent = new GenerationEvent(minPos, size, genEnvironment, generatorMode, target, resultConsumer);
		
		try
		{
			worldGeneratorThreadPool.execute(() ->
			{
				try
				{
					long runStartTime = System.nanoTime();
					generationEvent.timeoutTime = runStartTime;
					generationEvent.inQueueTime = runStartTime - generationEvent.inQueueTime;
					generationEvent.timer = new EventTimer("setup");
					
					BatchGenerationEnvironment.isDhWorldGenThreadRef.set(true);
					
					
					genEnvironment.generateLodFromListAsync(generationEvent, (runnable) ->
					{
						worldGeneratorThreadPool.execute(() ->
						{
							// TODO why not just always set this each time?
							boolean alreadyMarked = BatchGenerationEnvironment.isThisDhWorldGenThread();
							if (!alreadyMarked)
							{
								BatchGenerationEnvironment.isDhWorldGenThreadRef.set(true);
							}
							
							try
							{
								runnable.run();
							}
							catch (Throwable throwable)
							{
								handleWorldGenThrowable(generationEvent, throwable);
							}
							finally
							{
								if (!alreadyMarked)
								{
									BatchGenerationEnvironment.isDhWorldGenThreadRef.set(false);
								}
							}
						});
					});
					
					generationEvent.future.complete(null);
				}
				catch (Throwable initialThrowable)
				{
					handleWorldGenThrowable(generationEvent, initialThrowable);
				}
				finally
				{
					BatchGenerationEnvironment.isDhWorldGenThreadRef.remove();
				}
			});
		}
		catch (RejectedExecutionException e)
		{
			generationEvent.future.completeExceptionally(e);
		}
		
		return generationEvent;
	}
	/** There's probably a better way to handle this, but it'll work for now */
	private static void handleWorldGenThrowable(GenerationEvent generationEvent, Throwable initialThrowable)
	{
		Throwable throwable = initialThrowable;
		while (throwable instanceof CompletionException)
		{
			throwable = throwable.getCause();
		}
		
		boolean isShutdownException = ExceptionUtil.isShutdownException(throwable);
		if (isShutdownException)
		{
			// these exceptions can be ignored, generally they just mean
			// the thread is busy so it'll need to try again later.
			// FIXME this should cause the world gen task to be re-queued so we can try again later
			//  however, currently it can cause large gaps in the world gen instead.
			//  These gaps will generate correctly if the level is reloaded and the world gen is re-queued,
			//  however this is makes it look like the generator isn't working or skipped something.
		}
		else
		{
			generationEvent.future.completeExceptionally(throwable);
		}
	}
	
	public boolean isComplete() { return this.future.isDone(); }
	
	public boolean hasTimeout(int duration, TimeUnit unit)
	{
		if (this.timeoutTime == -1)
		{
			return false;
		}
		
		long currentTime = System.nanoTime();
		long delta = currentTime - this.timeoutTime;
		return (delta > TimeUnit.NANOSECONDS.convert(duration, unit));
	}
	
	public boolean terminate()
	{
		LOGGER.info("======================DUMPING ALL THREADS FOR WORLD GEN=======================");
		ThreadPoolUtil.WORLD_GEN_THREAD_FACTORY.dumpAllThreadStacks();
		this.future.cancel(true);
		return this.future.isCancelled();
	}
	
	public void refreshTimeout()
	{
		this.timeoutTime = System.nanoTime();
		UncheckedInterruptedException.throwIfInterrupted();
	}
	
	@Override
	public String toString() { return this.id + ":" + this.widthInChunks + "@" + this.minPos + "(" + this.targetGenerationStep + ")"; }
	
}