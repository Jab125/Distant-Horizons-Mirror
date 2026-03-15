package com.seibel.distanthorizons.common.wrappers.gui.config;

import com.seibel.distanthorizons.core.config.gui.IConfigGuiInfo;
import com.seibel.distanthorizons.core.config.types.AbstractConfigBase;
#if MC_VER <= MC_1_12_2
import com.seibel.distanthorizons.common.wrappers.gui.OnPressed;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.text.ITextComponent;
#else
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
#endif
import org.jetbrains.annotations.Nullable;

import java.util.AbstractMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

/** 
 * holds information needed by the config GUI for rendering.
 * 
 * @see AbstractConfigBase
 */
public class ConfigGuiInfo implements IConfigGuiInfo
{
	/**
	 * Used to display validation errors.
	 * Null if no error is present.
	 */

	@Nullable
	public #if MC_VER <= MC_1_12_2 ITextComponent #else Component #endif errorMessage;
	
	public BiFunction<#if MC_VER <= MC_1_12_2 GuiTextField #else EditBox #endif,#if MC_VER <= MC_1_12_2 GuiButton #else Button #endif, Predicate<String>> tooltipFunction;
	/** determines which options the button will show */
	public AbstractMap.SimpleEntry<#if MC_VER <= MC_1_12_2 OnPressed #else Button.OnPress #endif, Function<Object, #if MC_VER <= MC_1_12_2 ITextComponent #else Component #endif>> buttonOptionMap;
	
}
