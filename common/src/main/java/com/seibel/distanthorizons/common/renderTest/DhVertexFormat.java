package com.seibel.distanthorizons.common.renderTest;

import com.mojang.blaze3d.vertex.VertexFormatElement;

public class DhVertexFormat
{
	public static final VertexFormatElement SCREEN_POS = VertexFormatElement.register(/*id*/7, /*index*/0, VertexFormatElement.Type.FLOAT, VertexFormatElement.Usage.POSITION, /*count*/ 2);
	public static final VertexFormatElement RGBA_FLOAT_COLOR = VertexFormatElement.register(/*id*/8, /*index*/0, VertexFormatElement.Type.FLOAT, VertexFormatElement.Usage.COLOR, /*count*/ 4);
	
	public static final VertexFormatElement SHORT_POS = VertexFormatElement.register(/*id*/9, /*index*/0, VertexFormatElement.Type.USHORT, VertexFormatElement.Usage.POSITION, /*count*/ 3);
	public static final VertexFormatElement BYTE_PAD = VertexFormatElement.register(/*id*/10, /*index*/0, VertexFormatElement.Type.BYTE, VertexFormatElement.Usage.GENERIC, /*count*/ 1);
	public static final VertexFormatElement LIGHT = VertexFormatElement.register(/*id*/11, /*index*/0, VertexFormatElement.Type.UBYTE, VertexFormatElement.Usage.GENERIC, /*count*/ 1);
	public static final VertexFormatElement RGBA_UBYTE_COLOR = VertexFormatElement.register(/*id*/12, /*index*/0, VertexFormatElement.Type.UBYTE, VertexFormatElement.Usage.COLOR, /*count*/ 4);
	public static final VertexFormatElement IRIS_MATERIAL = VertexFormatElement.register(/*id*/13, /*index*/0, VertexFormatElement.Type.BYTE, VertexFormatElement.Usage.GENERIC, /*count*/ 1);
	public static final VertexFormatElement IRIS_NORMAL = VertexFormatElement.register(/*id*/14, /*index*/0, VertexFormatElement.Type.BYTE, VertexFormatElement.Usage.GENERIC, /*count*/ 1);
	
	
}
