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

package com.seibel.distanthorizons.fabric.mixins.client;

import com.seibel.distanthorizons.common.wrappers.gui.GetConfigScreen;
import com.seibel.distanthorizons.common.wrappers.gui.TexturedButtonWidget;
import com.seibel.distanthorizons.coreapi.ModInfo;
import com.seibel.distanthorizons.core.config.Config;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.screens.OptionsScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
#if MC_VER < MC_1_19_2
import net.minecraft.network.chat.TranslatableComponent;
#endif
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Objects;

/**
 * Adds a button to the menu to goto the config
 *
 * @author coolGi
 * @version 12-02-2021
 */
@Mixin(OptionsScreen.class)
public class MixinOptionsScreen extends Screen
{
	/** Get the texture for the button */
	private static final ResourceLocation ICON_TEXTURE = new ResourceLocation(ModInfo.ID, "textures/gui/button.png");
	
	public final TexturedButtonWidget optionsButton;
	
	@Shadow
	@Final
	protected HeaderAndFooterLayout layout;
	
	
	
	protected MixinOptionsScreen(Component title) 
	{ 
		super(title);
		
		this.optionsButton = new TexturedButtonWidget(
				// Where the button is on the screen
				this.width / 2 - 180, this.height / 6 - 12,
				// Width and height of the button
				20, 20,
				// texture UV Offset
				0, 0,
				// Some textuary stuff
				20, ICON_TEXTURE, 20, 40,
				// Create the button and tell it where to go
				// For now it goes to the client option by default
				(buttonWidget) -> Objects.requireNonNull(minecraft).setScreen(GetConfigScreen.getScreen(this)),
				// Add a title to the utton
                #if MC_VER < MC_1_19_2
					new TranslatableComponent(ModInfo.ID + ".title")));
                #else
					Component.translatable(ModInfo.ID + ".title"));
				#endif
	}
	
	
	
	@Inject(at = @At("HEAD"), method = "init")
	private void lodconfig$init(CallbackInfo ci)
	{
		if (Config.Client.optionsButton.get())
		{
			this. #if MC_VER < MC_1_17_1 addButton #else addRenderableWidget #endif
					(this.optionsButton);
			
			// TODO we need to add optionsButton to the UI via the layout instead so it centers correctly
			//  This wasn't working on James' machine
			this.layout.visitChildren((x) -> 
			{
				System.out.println(x);
			});
			
		}
	}
	
	
	//@Inject(method = "Lnet/minecraft/client/gui/screens/Screen;render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V", at = @At(value = "INVOKE"))
	//public void renderStuff(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci)
	//{
	//	int newWidth = this.width / 2 - 180;
	//	if (this.optionsButton.getX() != newWidth)
	//	{
	//		this.optionsButton.setX(newWidth);
	//	}
	//}
	
}
