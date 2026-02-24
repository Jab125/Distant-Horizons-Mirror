package com.seibel.distanthorizons.common.renderTest;

import com.mojang.blaze3d.vertex.VertexFormatElement;

public class DhVertexFormat
{
	
	public static final VertexFormatElement SCREEN_POS = VertexFormatElement.register(/*id*/7, /*index*/0, VertexFormatElement.Type.FLOAT, VertexFormatElement.Usage.POSITION, /*count*/ 2);
	public static final VertexFormatElement RGBA_COLOR = VertexFormatElement.register(/*id*/8, /*index*/0, VertexFormatElement.Type.FLOAT, VertexFormatElement.Usage.COLOR, /*count*/ 4);
	
}
