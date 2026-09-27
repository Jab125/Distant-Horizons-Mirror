package com.seibel.distanthorizons.forge17.modAccessor;

import com.seibel.distanthorizons.common.wrappers.modAccessor.IGtnhLibCommonAccessor;

import com.gtnewhorizon.gtnhlib.util.AboveHotbarHUD;

public class GtnhLibAccessor implements IGtnhLibCommonAccessor
{
	@Override
	public String getModName() { return "gtnhlib"; }
	
	@Override
	public void renderTextAboveHotbar(String text)
	{ AboveHotbarHUD.renderTextAboveHotbar("§f" + text, 60, true, true); }
	
}
