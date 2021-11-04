package com.seibel.lod.wrappers;

import com.mojang.math.Matrix4f;

/**
 * For wrapping around Matrix4f
 * @author Ran
 */
public class Matrix4fWrapper extends Matrix4f {
    private float[] values;

    public Matrix4fWrapper() {
    }

    public Matrix4fWrapper(float[] floatArray) {
        this.values = floatArray;
    }

    public Matrix4f floatArrayToMatrix(float[] floatArray) {
        this.values = floatArray;
        return floatArrayToMatrix();
    }

    public Matrix4f floatArrayToMatrix() {
        this.m00 = values[0];
        this.m01 = values[1];
        this.m02 = values[2];
        this.m03 = values[3];
        this.m10 = values[4];
        this.m11 = values[5];
        this.m12 = values[6];
        this.m13 = values[7];
        this.m20 = values[8];
        this.m21 = values[9];
        this.m22 = values[10];
        this.m23 = values[11];
        this.m30 = values[12];
        this.m31 = values[13];
        this.m32 = values[14];
        this.m33 = values[15];
        return new Matrix4f(this);
    }
}
