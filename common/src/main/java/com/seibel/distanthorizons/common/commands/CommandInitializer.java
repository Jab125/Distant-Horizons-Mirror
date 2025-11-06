package com.seibel.distanthorizons.common.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;

import static com.seibel.distanthorizons.core.network.messages.MessageRegistry.DEBUG_CODEC_CRASH_MESSAGE;
import static net.minecraft.commands.Commands.literal;

/**
 * Initializes commands of the mod.
 */
public class CommandInitializer
{
	private boolean serverReady = false;
	
	private CommandDispatcher<CommandSourceStack> commandDispatcher;
	
	/**
	 * Constructs a new instance of this class.
	 */
	public CommandInitializer() {}
	
	/**
	 * Notify the command initializer that the game is ready to accept commands.
	 * If {@link CommandInitializer#initCommands(CommandDispatcher)} has been fired before it was ready, it will also initialize the commands.
	 */
	public void onServerReady() {
		serverReady = true;
		if (commandDispatcher != null)
		{
			initCommands(commandDispatcher);
			commandDispatcher = null;
		}
	}
	
	/**
	 * Initializes all available commands.
	 * If the game is not ready yet, it stores the dispatcher to initialize the commands later.
	 * 
	 * @param commandDispatcher The command dispatcher to register commands to.
	 */
	public void initCommands(CommandDispatcher<CommandSourceStack> commandDispatcher)
	{
		if (!serverReady)
		{
			this.commandDispatcher = commandDispatcher;
			return;
		}
		
		LiteralArgumentBuilder<CommandSourceStack> builder = literal("dh")
				.requires(source -> source.hasPermission(4));
		
		builder.then(new ConfigCommand().buildCommand());
		builder.then(new DebugCommand().buildCommand());
		builder.then(new PregenCommand().buildCommand());
		
		if (DEBUG_CODEC_CRASH_MESSAGE)
		{
			builder.then(new CrashCommand().buildCommand());
		}
		
		commandDispatcher.register(builder);
	}
	
}
