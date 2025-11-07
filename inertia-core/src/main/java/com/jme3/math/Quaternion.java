/*
 * Copyright (c) 2009-2023 jMonkeyEngine
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 * * Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright
 *   notice, this list of conditions and the following disclaimer in the
 *   documentation and/or other materials provided with the distribution.
 *
 * * Neither the name of 'jMonkeyEngine' nor the names of its contributors
 *   may be used to endorse or promote products derived from this software
 *   without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.jme3.math;

import com.jme3.util.TempVars;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.Vector;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Iterator;
import java.util.logging.Logger;

/**
 * Used to efficiently represent rotations and orientations in 3-dimensional
 * space, without risk of gimbal lock. Each instance has 4 single-precision
 * components: 3 imaginary components (X, Y, and Z) and a real component (W).
 *
 *
 * <p>Mathematically, quaternions are an extension of complex numbers. In
 * mathematics texts, W often appears first, but in JME it always comes last.
 *
 * @author Mark Powell
 * @author Joshua Slack
 */
public final class Quaternion implements Cloneable, java.io.Serializable {

    static final long serialVersionUID = 1;
    private static final Quaternion rotator = new Quaternion(0, 0, 0, 0);

    // ********************************************************
    // Loggers
    private static final Logger logger = Logger.getLogger(Quaternion.class.getName());

    // ********************************************************
    // Misc
    public static final double SINGULARITY_NORTH_POLE = 0.49999;
    public static final double SINGULARITY_SOUTH_POLE = -0.49999;

    // ********************************************************
    // Statics
    public static final Quaternion IDENTITY = new Quaternion();

    public static final Quaternion DIRECTION_Z = new Quaternion();

    public static final Quaternion ZERO = new Quaternion(0, 0, 0, 0);

    static {
        DIRECTION_Z.fromAxes(Vector3f.UNIT_X, Vector3f.UNIT_Y, Vector3f.UNIT_Z);
    }

    /** The first imaginary (X) component. Not an angle!*/
    protected float x = 0.0f;
    /** The 2nd imaginary (Y) component. Not an angle! */
    protected float y = 0.0f;
    /** The 3rd imaginary (Z) component. Not an angle! */
    protected float z = 0.0f;
    /** The real (W) component. Not an angle! */
    protected float w = 1.0f;

    /**
     * Instantiates an identity quaternion: all components zeroed except
     * {@code w}, which is set to 1.
     */
    public Quaternion() {
        this.x = 0.0f;
        this.y = 0.0f;
        this.z = 0.0f;
        this.w = 1.0f;
    }

    /**
     * Instantiates a quaternion with the specified components.
     *
     * @param x the desired X component
     * @param y the desired Y component
     * @param z the desired Z component
     * @param w the desired W component
     */
    public Quaternion(float x, float y, float z, float w) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.w = w;
        this.normalizeLocal();
    }

    /**
     * Returns the X component. The quaternion is unaffected.
     *
     * @return the value of the {@link #x} component
     */
    public float getX() {
        return x;
    }
    public float x() {
        return x;
    }

    /**
     * Returns the Y component. The quaternion is unaffected.
     *
     * @return the value of the {@link #y} component
     */
    public float getY() {
        return y;
    }
    public float y() {
        return y;
    }

    /**
     * Returns the Z component. The quaternion is unaffected.
     *
     * @return the value of the {@link #z} component
     */
    public float getZ() {
        return z;
    }
    public float z() {
        return z;
    }

    /**
     * Returns the W (real) component. The quaternion is unaffected.
     *
     * @return the value of the {@link #w} component
     */
    public float getW() {
        return w;
    }
    public float w() {
        return w;
    }

    /**
     * Sets all 4 components to specified values.
     *
     * @param x the desired X component
     * @param y the desired Y component
     * @param z the desired Z component
     * @param w the desired W component
     * @return the (modified) current instance (for chaining)
     */
    public Quaternion set(float x, float y, float z, float w) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.w = w;
        return this;
    }

    /**
     * Copies all 4 components from the argument.
     *
     * @param q the quaternion to copy (not null, unaffected)
     * @return the (modified) current instance (for chaining)
     */
    public Quaternion set(Quaternion q) {
        this.x = q.x;
        this.y = q.y;
        this.z = q.z;
        this.w = q.w;
        return this;
    }

    /**
     * Instantiates a quaternion from Tait-Bryan angles, applying the rotations
     * in x-z-y extrinsic order or y-z'-x" intrinsic order.
     *
     * @param angles an array of Tait-Bryan angles (in radians, exactly 3
     *     elements, the X angle in {@code angles[0]}, the Y angle in {@code
     *     angles[1]}, and the Z angle in {@code angles[2]}, not null,
     *     unaffected)
     */
    public Quaternion(float[] angles) {
        fromAngles(angles);
    }

    /**
     * Instantiates a quaternion by interpolating between the specified
     * quaternions.
     *
     * <p>Uses
     * {@link #slerp(Quaternion, Quaternion, float)},
     * which is fast but inaccurate.
     *
     * @param q1 the desired value when interp=0 (not null, unaffected)
     * @param q2 the desired value when interp=1 (not null, may be modified)
     * @param interp the fractional change amount
     */
    public Quaternion(Quaternion q1, Quaternion q2, float interp) {
        slerp(q1, q2, interp);
    }

    /**
     * Instantiates a copy of the argument.
     *
     * @param q the quaternion to copy (not null, unaffected)
     */
    public Quaternion(Quaternion q) {
        this.x = q.x;
        this.y = q.y;
        this.z = q.z;
        this.w = q.w;
    }

    /**
     * Sets all components to zero except {@code w}, which is set to 1.
     */
    public void setIdentity() {
        x = y = z = 0;
        w = 1;
    }
    /**
     * Sets all components to zero except {@code w}, which is set to 1.
     */
    @Deprecated
    public void loadIdentity() {
        x = y = z = 0;
        w = 1;
    }

    /**
     * Compares with the identity quaternion, without distinguishing -0 from 0.
     * The current instance is unaffected.
     *
     * @return true if the current quaternion equals the identity, otherwise
     *     false
     */
    public boolean isIdentity() {
        return x == 0 && y == 0 && z == 0 && w == 1;
    }

    /**
     * Tests for approximate equality with the specified quaternion, using the
     * specified tolerance. The current instance is unaffected.
     *
     * <p>To quantify the similarity of 2 normalized quaternions, use
     * {@link #dot(Quaternion)}.
     *
     * @param other the quaternion to compare (not null, unaffected)
     * @param epsilon the tolerance for each component
     * @return true if all 4 components are within tolerance, otherwise false
     */
    public boolean isSimilar(Quaternion other, float epsilon) {
        if (other == null) {
            return false;
        }
        if (Float.compare(FastMath.abs(other.x - x), epsilon) > 0) {
            return false;
        }
        if (Float.compare(FastMath.abs(other.y - y), epsilon) > 0) {
            return false;
        }
        if (Float.compare(FastMath.abs(other.z - z), epsilon) > 0) {
            return false;
        }
        if (Float.compare(FastMath.abs(other.w - w), epsilon) > 0) {
            return false;
        }
        return true;
    }

    public boolean isZero() {
        return (x == 0.0F && y == 0.0F && getZ() == 0.0F && w == 1.0F);
    }

    /** Get the pole of the gimbal lock, if any.
     * @return positive (+1) for north pole, negative (-1) for south pole, zero (0) when no gimbal lock */
    public int getGimbalPole () {
        final float t = y * x + z * w;
        return t > 0.499f ? 1 : (t < -0.499f ? -1 : 0);
    }

    /**
     * Sets the quaternion from the specified Tait-Bryan angles, applying the
     * rotations in x-z-y extrinsic order or y-z'-x" intrinsic order.
     *
     * @param angles an array of Tait-Bryan angles (in radians, exactly 3
     *     elements, the X angle in {@code angles[0]}, the Y angle in {@code
     *     angles[1]}, and the Z angle in {@code angles[2]}, not null,
     *     unaffected)
     * @return the (modified) current instance (for chaining)
     * @throws IllegalArgumentException if {@code angles.length != 3}
     */
    public Quaternion fromAngles(float[] angles) {
        if (angles.length != 3) {
            throw new IllegalArgumentException(
                    "Angles array must have three elements");
        }

        return fromAngles(angles[0], angles[1], angles[2]);
    }

    /**
     * Sets the quaternion from the specified Tait-Bryan angles, applying the
     * rotations in x-z-y extrinsic order or y-z'-x" intrinsic order.
     *
     * @see
     * <a href="http://www.euclideanspace.com/maths/geometry/rotations/conversions/eulerToQuaternion/index.htm">http://www.euclideanspace.com/maths/geometry/rotations/conversions/eulerToQuaternion/index.htm</a>
     *
     * @param xAngle the X angle (in radians)
     * @param yAngle the Y angle (in radians)
     * @param zAngle the Z angle (in radians)
     * @return the (modified) current instance (for chaining)
     */
    public Quaternion fromAngles(float xAngle, float yAngle, float zAngle) {
        float angle;
        float sinY, sinZ, sinX, cosY, cosZ, cosX;
        angle = zAngle * 0.5f;
        sinZ = FastMath.sin(angle);
        cosZ = FastMath.cos(angle);
        angle = yAngle * 0.5f;
        sinY = FastMath.sin(angle);
        cosY = FastMath.cos(angle);
        angle = xAngle * 0.5f;
        sinX = FastMath.sin(angle);
        cosX = FastMath.cos(angle);

        // variables used to reduce multiplication calls.
        float cosYXcosZ = cosY * cosZ;
        float sinYXsinZ = sinY * sinZ;
        float cosYXsinZ = cosY * sinZ;
        float sinYXcosZ = sinY * cosZ;

        w = (cosYXcosZ * cosX - sinYXsinZ * sinX);
        x = (cosYXcosZ * sinX + sinYXsinZ * cosX);
        y = (sinYXcosZ * cosX + cosYXsinZ * sinX);
        z = (cosYXsinZ * cosX - sinYXcosZ * sinX);

        normalizeLocal();
        return this;
    }

    /**
     * Converts to equivalent Tait-Bryan angles, to be applied in x-z-y
     * intrinsic order or y-z'-x" extrinsic order, for instance by
     * {@link #fromAngles(float[])}. The current instance is unaffected.
     *
     * @see
     * <a href=
     * "http://www.euclideanspace.com/maths/geometry/rotations/conversions/quaternionToEuler/index.htm">
     * http://www.euclideanspace.com/maths/geometry/rotations/conversions/quaternionToEuler/index.htm
     * </a>
     *
     * @param angles storage for the result, or null for a new float[3]
     * @return an array of 3 angles (in radians, either <code>angles</code> or a
     *     new float[3], the X angle in angles[0], the Y angle in angles[1], and
     *     the Z angle in angles[2])
     * @throws IllegalArgumentException if {@code angles.length != 3}
     */
    public float[] toAngles(float[] angles) {
        if (angles == null) {
            angles = new float[3];
        } else if (angles.length != 3) {
            throw new IllegalArgumentException("Angles array must have three elements");
        }

        float sqw = w * w;
        float sqx = x * x;
        float sqy = y * y;
        float sqz = z * z;
        float unit = sqx + sqy + sqz + sqw; // if normalized is one, otherwise
        // is correction factor
        float test = x * y + z * w;
        if (test > 0.499 * unit) { // singularity at north pole
            angles[1] = 2 * FastMath.atan2(x, w);
            angles[2] = FastMath.HALF_PI;
            angles[0] = 0;
        } else if (test < -0.499 * unit) { // singularity at south pole
            angles[1] = -2 * FastMath.atan2(x, w);
            angles[2] = -FastMath.HALF_PI;
            angles[0] = 0;
        } else {
            angles[1] = FastMath.atan2(2 * y * w - 2 * x * z, sqx - sqy - sqz + sqw); // yaw or heading
            angles[2] = FastMath.asin(2 * test / unit); // roll or bank
            angles[0] = FastMath.atan2(2 * x * w - 2 * y * z, -sqx + sqy - sqz + sqw); // pitch or attitude
        }
        return angles;
    }

    /**
     * Sets the quaternion from the specified rotation matrix. Does not verify
     * that the argument is a valid rotation matrix.
     *
     * @param matrix the input matrix (not null, unaffected)
     * @return the (modified) current instance (for chaining)
     */
    public Quaternion fromRotationMatrix(Matrix3f matrix) {
        return fromRotationMatrix(matrix.m00, matrix.m01, matrix.m02, matrix.m10,
                matrix.m11, matrix.m12, matrix.m20, matrix.m21, matrix.m22);
    }

    /**
     * Sets the quaternion from a rotation matrix with the specified elements.
     * Does not verify that the arguments form a valid rotation matrix.
     *
     * @param m00 the matrix element in row 0, column 0
     * @param m01 the matrix element in row 0, column 1
     * @param m02 the matrix element in row 0, column 2
     * @param m10 the matrix element in row 1, column 0
     * @param m11 the matrix element in row 1, column 1
     * @param m12 the matrix element in row 1, column 2
     * @param m20 the matrix element in row 2, column 0
     * @param m21 the matrix element in row 2, column 1
     * @param m22 the matrix element in row 2, column 2
     * @return the (modified) current instance (for chaining)
     */
    public Quaternion fromRotationMatrix(float m00, float m01, float m02,
                                         float m10, float m11, float m12,
                                         float m20, float m21, float m22
    ) {
        // first normalize the forward (F), up (U) and side (S) vectors of the rotation matrix
        // so that the scale does not affect the rotation
        float lengthSquared = m00 * m00 + m10 * m10 + m20 * m20;
        if (lengthSquared != 1f && lengthSquared != 0f) {
            lengthSquared = 1.0f / FastMath.sqrt(lengthSquared);
            m00 *= lengthSquared;
            m10 *= lengthSquared;
            m20 *= lengthSquared;
        }
        lengthSquared = m01 * m01 + m11 * m11 + m21 * m21;
        if (lengthSquared != 1f && lengthSquared != 0f) {
            lengthSquared = 1.0f / FastMath.sqrt(lengthSquared);
            m01 *= lengthSquared;
            m11 *= lengthSquared;
            m21 *= lengthSquared;
        }
        lengthSquared = m02 * m02 + m12 * m12 + m22 * m22;
        if (lengthSquared != 1f && lengthSquared != 0f) {
            lengthSquared = 1.0f / FastMath.sqrt(lengthSquared);
            m02 *= lengthSquared;
            m12 *= lengthSquared;
            m22 *= lengthSquared;
        }

        // Use the Graphics Gems code, from
        // ftp://ftp.cis.upenn.edu/pub/graphics/shoemake/quatut.ps.Z
        // *NOT* the "Matrix and Quaternions FAQ", which has errors!

        // the trace is the sum of the diagonal elements; see
        // http://mathworld.wolfram.com/MatrixTrace.html
        float t = m00 + m11 + m22;

        // we protect the division by s by ensuring that s>=1
        if (t >= 0) { // |w| >= .5
            float s = FastMath.sqrt(t + 1); // |s|>=1 ...
            w = 0.5f * s;
            s = 0.5f / s;                 // so this division isn't bad
            x = (m21 - m12) * s;
            y = (m02 - m20) * s;
            z = (m10 - m01) * s;
        } else if ((m00 > m11) && (m00 > m22)) {
            float s = FastMath.sqrt(1.0f + m00 - m11 - m22); // |s|>=1
            x = s * 0.5f; // |x| >= .5
            s = 0.5f / s;
            y = (m10 + m01) * s;
            z = (m02 + m20) * s;
            w = (m21 - m12) * s;
        } else if (m11 > m22) {
            float s = FastMath.sqrt(1.0f + m11 - m00 - m22); // |s|>=1
            y = s * 0.5f; // |y| >= .5
            s = 0.5f / s;
            x = (m10 + m01) * s;
            z = (m21 + m12) * s;
            w = (m02 - m20) * s;
        } else {
            float s = FastMath.sqrt(1.0f + m22 - m00 - m11); // |s|>=1
            z = s * 0.5f; // |z| >= .5
            s = 0.5f / s;
            x = (m02 + m20) * s;
            y = (m21 + m12) * s;
            w = (m10 - m01) * s;
        }

        return this;
    }

    /**
     * Converts to an equivalent rotation matrix. The current instance is
     * unaffected.
     *
     * <p>Note: the result is created from a normalized version of the current
     * instance.
     *
     * @return a new 3x3 rotation matrix
     */
    public Matrix3f toRotationMatrix() {
        Matrix3f matrix = new Matrix3f();
        return toRotationMatrix(matrix);
    }

    /**
     * Converts to an equivalent rotation matrix. The current instance is
     * unaffected.
     *
     * <p>Note: the result is created from a normalized version of the current
     * instance.
     *
     * @param store storage for the result (not null)
     * @return {@code result}, configured as a 3x3 rotation matrix
     */
    public Matrix3f toRotationMatrix(Matrix3f store) {

        float norm = norm();
        // we explicitly test norm against one here, saving a division
        // at the cost of a test and branch.  Is it worth it?
        float s = (norm == 1f) ? 2f : (norm > 0f) ? 2f / norm : 0;

        // compute xs/ys/zs first to save 6 multiplications, since xs/ys/zs
        // will be used 2-4 times each.
        float xs = x * s;
        float ys = y * s;
        float zs = z * s;
        float xx = x * xs;
        float xy = x * ys;
        float xz = x * zs;
        float xw = w * xs;
        float yy = y * ys;
        float yz = y * zs;
        float yw = w * ys;
        float zz = z * zs;
        float zw = w * zs;

        // using s=2/norm (instead of 1/norm) saves 9 multiplications by 2 here
        store.m00 = 1 - (yy + zz);
        store.m01 = (xy - zw);
        store.m02 = (xz + yw);
        store.m10 = (xy + zw);
        store.m11 = 1 - (xx + zz);
        store.m12 = (yz - xw);
        store.m20 = (xz - yw);
        store.m21 = (yz + xw);
        store.m22 = 1 - (xx + yy);

        return store;
    }

    /**
     * Sets the rotation component of the specified transform matrix. The
     * current instance is unaffected.
     *
     * <p>Note: preserves the translation component of {@code store} but not
     * its scaling component.
     *
     * <p>Note: the result is created from a normalized version of the current
     * instance.
     *
     * @param store storage for the result (not null)
     * @return {@code store}, with 9 of its 16 elements modified
     */
    public Matrix4f toTransformMatrix(Matrix4f store) {

        float norm = norm();
        // we explicitly test norm against one here, saving a division
        // at the cost of a test and branch.  Is it worth it?
        float s = (norm == 1f) ? 2f : (norm > 0f) ? 2f / norm : 0;

        // compute xs/ys/zs first to save 6 multiplications, since xs/ys/zs
        // will be used 2-4 times each.
        float xs = x * s;
        float ys = y * s;
        float zs = z * s;
        float xx = x * xs;
        float xy = x * ys;
        float xz = x * zs;
        float xw = w * xs;
        float yy = y * ys;
        float yz = y * zs;
        float yw = w * ys;
        float zz = z * zs;
        float zw = w * zs;

        // using s=2/norm (instead of 1/norm) saves 9 multiplications by 2 here
        store.m00 = 1 - (yy + zz);
        store.m01 = (xy - zw);
        store.m02 = (xz + yw);
        store.m10 = (xy + zw);
        store.m11 = 1 - (xx + zz);
        store.m12 = (yz - xw);
        store.m20 = (xz - yw);
        store.m21 = (yz + xw);
        store.m22 = 1 - (xx + yy);

        return store;
    }

    /**
     * Sets the rotation component of the specified transform matrix. The
     * current instance is unaffected.
     *
     * <p>Note: preserves the translation and scaling components of
     * {@code result}.
     *
     * <p>Note: the result is created from a normalized version of the current
     * instance.
     *
     * @param store storage for the result (not null)
     * @return {@code result}, with 9 of its 16 elements modified
     */
    public Matrix4f toRotationMatrix(Matrix4f store) {
        TempVars tempv = TempVars.get();
        Vector3f originalScale = tempv.vect1;

        store.toScaleVector(originalScale);
        store.setScale(1, 1, 1);
        float norm = norm();
        // we explicitly test norm against one here, saving a division
        // at the cost of a test and branch.  Is it worth it?
        float s = (norm == 1f) ? 2f : (norm > 0f) ? 2f / norm : 0;

        // compute xs/ys/zs first to save 6 multiplications, since xs/ys/zs
        // will be used 2-4 times each.
        float xs = x * s;
        float ys = y * s;
        float zs = z * s;
        float xx = x * xs;
        float xy = x * ys;
        float xz = x * zs;
        float xw = w * xs;
        float yy = y * ys;
        float yz = y * zs;
        float yw = w * ys;
        float zz = z * zs;
        float zw = w * zs;

        // using s=2/norm (instead of 1/norm) saves 9 multiplications by 2 here
        store.m00 = 1 - (yy + zz);
        store.m01 = (xy - zw);
        store.m02 = (xz + yw);
        store.m10 = (xy + zw);
        store.m11 = 1 - (xx + zz);
        store.m12 = (yz - xw);
        store.m20 = (xz - yw);
        store.m21 = (yz + xw);
        store.m22 = 1 - (xx + yy);

        store.setScale(originalScale);

        tempv.release();

        return store;
    }

    /**
     * Calculates one of the basis vectors of the rotation. The current instance
     * is unaffected.
     *
     * <p>Note: the result is created from a normalized version of the current
     * instance.
     *
     * @param i which basis vector to retrieve (&ge;0, &lt;3, 0&rarr;X-axis,
     *     1&rarr;Y-axis, 2&rarr;Z-axis)
     * @return the basis vector (a new Vector3f)
     */
    public Vector3f getRotationColumn(int i) {
        return getRotationColumn(i, null);
    }

    /**
     * Calculates one of the basis vectors of the rotation. The current instance
     * is unaffected.
     *
     * <p>Note: the result is created from a normalized version of the current
     * instance.
     *
     * @param i which basis vector to retrieve (&ge;0, &lt;3, 0&rarr;X-axis,
     *     1&rarr;Y-axis, 2&rarr;Z-axis)
     * @param store storage for the result, or null for a new Vector3f
     * @return the basis vector (either <code>store</code> or a new Vector3f)
     * @throws IllegalArgumentException if index is not 0, 1, or 2
     */
    public Vector3f getRotationColumn(int i, Vector3f store) {
        if (store == null) {
            store = new Vector3f();
        }

        float norm = norm();
        if (norm != 1.0f) {
            norm = 1.0f / norm;
        }

        float xx = x * x * norm;
        float xy = x * y * norm;
        float xz = x * z * norm;
        float xw = x * w * norm;
        float yy = y * y * norm;
        float yz = y * z * norm;
        float yw = y * w * norm;
        float zz = z * z * norm;
        float zw = z * w * norm;

        switch (i) {
            case 0:
                store.x = 1 - 2 * (yy + zz);
                store.y = 2 * (xy + zw);
                store.z = 2 * (xz - yw);
                break;
            case 1:
                store.x = 2 * (xy - zw);
                store.y = 1 - 2 * (xx + zz);
                store.z = 2 * (yz + xw);
                break;
            case 2:
                store.x = 2 * (xz + yw);
                store.y = 2 * (yz - xw);
                store.z = 1 - 2 * (xx + yy);
                break;
            default:
                logger.warning("Invalid column index.");
                throw new IllegalArgumentException("Invalid column index. " + i);
        }

        return store;
    }

    /**
     * Sets the quaternion from the specified rotation angle and axis of
     * rotation. This method creates garbage, so use
     * {@link #fromAngleNormalAxis(float, Vector3f)} if the axis
     * is known to be normalized.
     *
     * @param angle the desired rotation angle (in radians)
     * @param axis the desired axis of rotation (not null, unaffected)
     * @return the (modified) current instance (for chaining)
     */
    public Quaternion fromAngleAxis(float angle, Vector3f axis) {
        Vector3f normAxis = axis.normalize();
        fromAngleNormalAxis(angle, normAxis);
        return this;
    }

    /**
     * Sets the quaternion from the specified rotation angle and normalized axis
     * of rotation. If the axis might not be normalized, use
     * {@link #fromAngleAxis(float, Vector3f)} instead.
     *
     * @param angle the desired rotation angle (in radians)
     * @param axis the desired axis of rotation (not null, length=1, unaffected)
     * @return the (modified) current instance (for chaining)
     */
    public Quaternion fromAngleNormalAxis(float angle, Vector3f axis) {
        if (axis.x == 0 && axis.y == 0 && axis.z == 0) {
            loadIdentity();
        } else {
            float halfAngle = 0.5f * angle;
            float sin = FastMath.sin(halfAngle);
            w = FastMath.cos(halfAngle);
            x = sin * axis.x;
            y = sin * axis.y;
            z = sin * axis.z;
        }
        return this;
    }

    /**
     * Converts the quaternion to a rotation angle and axis of rotation, storing
     * the axis in the argument (if it's non-null) and returning the angle.
     *
     * <p>If the quaternion has {@code x*x + y*y + z*z == 0}, then (1,0,0) is
     * stored and 0 is returned. (This might happen if the rotation angle is
     * very close to 0.)
     *
     * <p>Otherwise, the quaternion is assumed to be normalized (norm=1). No
     * error checking is performed; the caller must ensure that the quaternion
     * is normalized.
     *
     * <p>In all cases, the current instance is unaffected.
     *
     * @param store storage for the axis (modified if not null)
     * @return the rotation angle (in radians)
     */
    public float toAngleAxis(Vector3f store) {
        float sqrLength = x * x + y * y + z * z;
        float angle;
        if (sqrLength == 0.0f) {
            angle = 0.0f;
            if (store != null) {
                store.x = 1.0f;
                store.y = 0.0f;
                store.z = 0.0f;
            }
        } else {
            angle = (2.0f * FastMath.acos(w));
            if (store != null) {
                float invLength = (1.0f / FastMath.sqrt(sqrLength));
                store.x = x * invLength;
                store.y = y * invLength;
                store.z = z * invLength;
            }
        }

        return angle;
    }

    /**
     * Interpolates between the specified quaternions and stores the result in
     * the current instance.
     *
     * @param q1 the desired value when interp=0 (not null, unaffected)
     * @param q2 the desired value when interp=1 (not null, may be modified)
     * @param t the fractional change amount
     * @return the (modified) current instance (for chaining)
     */
    public Quaternion slerp(Quaternion q1, Quaternion q2, float t) {
        return slerp(q1, q2, t, this);
    }

    public Quaternion slerp(Quaternion q1, Quaternion q2, float t, Quaternion store) {
        if (store == null) {
            store = new Quaternion();
        }

        // Create a local quaternion to store the interpolated quaternion
        if (q1.x == q2.x && q1.y == q2.y && q1.z == q2.z && q1.w == q2.w) {
            store.set(q1);
            return this;
        }

        float result =
                (q1.x * q2.x) +
                        (q1.y * q2.y) +
                        (q1.z * q2.z) +
                        (q1.w * q2.w);

        if (result < 0.0f) {
            // Negate the second quaternion and the result of the dot product
            q2.x = -q2.x;
            q2.y = -q2.y;
            q2.z = -q2.z;
            q2.w = -q2.w;
            result = -result;
        }

        // Set the first and second scale for the interpolation
        float scale0 = 1 - t;
        float scale1 = t;

        // Check if the angle between the 2 quaternions was big enough to
        // warrant such calculations
        if ((1 - result) > 0.1f) {// Get the angle between the 2 quaternions,
            // and then store the sin() of that angle
            float theta = FastMath.acos(result);
            float invSinTheta = 1f / FastMath.sin(theta);

            // Calculate the scale for q1 and q2, according to the angle and
            // its sine
            scale0 = FastMath.sin((1 - t) * theta) * invSinTheta;
            scale1 = FastMath.sin((t * theta)) * invSinTheta;
        }

        // Calculate the x, y, z and w values for the quaternion by using a
        // special
        // form of linear interpolation for quaternions.
        store.x = (scale0 * q1.x) + (scale1 * q2.x);
        store.y = (scale0 * q1.y) + (scale1 * q2.y);
        store.z = (scale0 * q1.z) + (scale1 * q2.z);
        store.w = (scale0 * q1.w) + (scale1 * q2.w);

        // Return the interpolated quaternion
        return store;
    }

    /**
     * Interpolates between the current instance and {@code q2} and stores the
     * result in the current instance.
     *
     * <p>This method is often more accurate than
     * {@link #nlerp(Quaternion, float)}, but slower.

     * @param q2 the desired value when changeAmnt=1 (not null, may be modified)
     * @param changeAmount the fractional change amount
     */
    public void slerp(Quaternion q2, float changeAmount) {
        if (this.x == q2.x && this.y == q2.y && this.z == q2.z && this.w == q2.w) {
            return;
        }

        float result = (this.x * q2.x) + (this.y * q2.y) + (this.z * q2.z)
                + (this.w * q2.w);

        if (result < 0.0f) {
            // Negate the second quaternion and the result of the dot product
            q2.x = -q2.x;
            q2.y = -q2.y;
            q2.z = -q2.z;
            q2.w = -q2.w;
            result = -result;
        }

        // Set the first and second scale for the interpolation
        float scale0 = 1 - changeAmount;
        float scale1 = changeAmount;

        // Check if the angle between the 2 quaternions was big enough to
        // warrant such calculations
        if ((1 - result) > 0.1f) {
            // Get the angle between the 2 quaternions, and then store the sin()
            // of that angle
            float theta = FastMath.acos(result);
            float invSinTheta = 1f / FastMath.sin(theta);

            // Calculate the scale for q1 and q2, according to the angle and
            // its sine
            scale0 = FastMath.sin((1 - changeAmount) * theta) * invSinTheta;
            scale1 = FastMath.sin((changeAmount * theta)) * invSinTheta;
        }

        // Calculate the x, y, z and w values for the quaternion by using a
        // special
        // form of linear interpolation for quaternions.
        this.x = (scale0 * this.x) + (scale1 * q2.x);
        this.y = (scale0 * this.y) + (scale1 * q2.y);
        this.z = (scale0 * this.z) + (scale1 * q2.z);
        this.w = (scale0 * this.w) + (scale1 * q2.w);
    }

    /**
     * Interpolates quickly between the current instance and {@code q2} using
     * nlerp, and stores the result in the current instance.
     *
     * <p>This method is often faster than
     * {@link #slerp(Quaternion, float)}, but less accurate.
     *
     * @param q2 the desired value when blend=1 (not null, unaffected)
     * @param blend the fractional change amount
     */
    public void nlerp(Quaternion q2, float blend) {
        float dot = dot(q2);
        float blendI = 1.0f - blend;
        if (dot < 0.0f) {
            x = blendI * x - blend * q2.x;
            y = blendI * y - blend * q2.y;
            z = blendI * z - blend * q2.z;
            w = blendI * w - blend * q2.w;
        } else {
            x = blendI * x + blend * q2.x;
            y = blendI * y + blend * q2.y;
            z = blendI * z + blend * q2.z;
            w = blendI * w + blend * q2.w;
        }
        normalizeLocal();
    }

    /**
     * Adds the argument and returns the sum as a new instance. The current
     * instance is unaffected.
     *
     * <p>Seldom used. To combine rotations, use
     * {@link #mult(Quaternion)} instead of this method.
     *
     * @param q the quaternion to add (not null, unaffected)
     * @return a new Quaternion
     */
    public Quaternion add(Quaternion q) {
        return new Quaternion(x + q.x, y + q.y, z + q.z, w + q.w);
    }

    /**
     * Adds the argument and returns the (modified) current instance.
     *
     * <p>Seldom used. To combine rotations, use
     * {@link #multLocal(Quaternion)} or
     * {@link #mult(Quaternion, Quaternion)}
     * instead of this method.
     *
     * @param q the quaternion to add (not null, unaffected unless it's
     *     {@code this})
     * @return the (modified) current instance (for chaining)
     */
    public Quaternion addLocal(Quaternion q) {
        this.x += q.x;
        this.y += q.y;
        this.z += q.z;
        this.w += q.w;
        return this;
    }

    /**
     * Subtracts the argument and returns difference as a new instance. The
     * current instance is unaffected.
     *
     * @param q the quaternion to subtract (not null, unaffected)
     * @return a new Quaternion
     */
    public Quaternion subtract(Quaternion q) {
        return new Quaternion(x - q.x, y - q.y, z - q.z, w - q.w);
    }

    /**
     * Subtracts the argument and returns the (modified) current instance.
     *
     * <p>To quantify the similarity of 2 normalized quaternions, use
     * {@link #dot(Quaternion)}.
     *
     * @param q the quaternion to subtract (not null, unaffected unless it's
     *     {@code this})
     * @return the (modified) current instance
     */
    public Quaternion subtractLocal(Quaternion q) {
        this.x -= q.x;
        this.y -= q.y;
        this.z -= q.z;
        this.w -= q.w;
        return this;
    }

    /**
     * Multiplies by the argument and returns the product as a new instance.
     * The current instance is unaffected.
     *
     * <p>This method is used to combine rotations. Note that quaternion
     * multiplication is noncommutative, so generally q * p != p * q.
     *
     * @param other the right factor (not null, unaffected)
     * @return {@code this * q} (a new Quaternion)
     */
    public Quaternion mult(Quaternion other) {
        if (other.isIdentity()) return new Quaternion(this);
        return mult(other, null);
    }

    /**
     * Multiplies by the specified quaternion and returns the product in a 3rd
     * quaternion. The current instance is unaffected, unless it's {@code storeResult}.
     *
     * <p>This method is used to combine rotations. Note that quaternion
     * multiplication is noncommutative, so generally q * p != p * q.
     *
     * <p>It is safe for {@code q} and {@code storeResult} to be the same object.
     * However, if {@code this} and {@code storeResult} are the same object, the result
     * is undefined.
     *
     * @param other the right factor (not null, unaffected unless it's {@code storeResult})
     * @param storeResult storage for the product, or null for a new Quaternion
     * @return {@code this * q} (either {@code storeResult} or a new Quaternion)
     */
    public Quaternion mult(Quaternion other, Quaternion storeResult) {
        if (storeResult == null) {
            storeResult = new Quaternion();
        }

        if (other.isIdentity()) return storeResult.set(this);

        storeResult.x = this.w * other.x + this.x * other.w + this.y * other.z - this.z * other.y;
        storeResult.y = this.w * other.y + this.y * other.w + this.z * other.x - this.x * other.z;
        storeResult.z = this.w * other.z + this.z * other.w + this.x * other.y - this.y * other.x;
        storeResult.w = this.w * other.w - this.x * other.x - this.y * other.y - this.z * other.z;

        return storeResult;
    }

    /**
     * Multiplies by the argument and returns the (modified) current instance.
     *
     * <p>This method is used to combine rotations. Note that quaternion
     * multiplication is noncommutative, so generally q * p != p * q.
     *
     * @param other the right factor (not null, unaffected unless it's {@code this})
     * @return the (modified) current instance (for chaining)
     */
    public Quaternion multLocal(Quaternion other) {
        if (other.isIdentity()) return this;

        final float newX = this.w * other.x + this.x * other.w + this.y * other.z - this.z * other.y;
        final float newY = this.w * other.y + this.y * other.w + this.z * other.x - this.x * other.z;
        final float newZ = this.w * other.z + this.z * other.w + this.x * other.y - this.y * other.x;
        final float newW = this.w * other.w - this.x * other.x - this.y * other.y - this.z * other.z;
        this.x = newX;
        this.y = newY;
        this.z = newZ;
        this.w = newW;
        return this;
    }

    /**
     * Multiplies by a quaternion with the specified components and returns the
     * (modified) current instance.
     *
     * <p>This method is used to combine rotations. Note that quaternion
     * multiplication is noncommutative, so generally q * p != p * q.
     *
     * @param x the X component of the right factor
     * @param y the Y component of the right factor
     * @param z the Z component of the right factor
     * @param w the W component of the right factor
     * @return the (modified) current instance (for chaining)
     */
    public Quaternion multLocal(float x, float y, float z, float w) {
        if (x == 0 && y == 0 && z == 0 && w == 1) return this;

        final float newX = this.w * x + this.x * w + this.y * z - this.z * y;
        final float newY = this.w * y + this.y * w + this.z * x - this.x * z;
        final float newZ = this.w * z + this.z * w + this.x * y - this.y * x;
        final float newW = this.w * w - this.x * x - this.y * y - this.z * z;
        this.x = newX;
        this.y = newY;
        this.z = newZ;
        this.w = newW;
        return this;
    }

    //

    /**
     * Multiplies by the argument and returns the product as a new instance.
     * The current instance is unaffected.
     *
     * <p>This method is used to combine rotations. Note that quaternion
     * multiplication is noncommutative, so generally q * p != p * q.
     *
     * @param other the right factor (not null, unaffected)
     * @return {@code this * q} (a new Quaternion)
     */
    public Quaternion multLeft(Quaternion other) {
        if (other.isIdentity()) return new Quaternion(this);
        return multLeft(other, null);
    }

    /**
     * Multiplies by the specified quaternion and returns the product in a 3rd
     * quaternion. The current instance is unaffected, unless it's {@code storeResult}.
     *
     * <p>This method is used to combine rotations. Note that quaternion
     * multiplication is noncommutative, so generally q * p != p * q.
     *
     * <p>It is safe for {@code q} and {@code storeResult} to be the same object.
     * However, if {@code this} and {@code storeResult} are the same object, the result
     * is undefined.
     *
     * @param other the right factor (not null, unaffected unless it's {@code storeResult})
     * @param storeResult storage for the product, or null for a new Quaternion
     * @return {@code this * q} (either {@code storeResult} or a new Quaternion)
     */
    public Quaternion multLeft(Quaternion other, Quaternion storeResult) {
        if (storeResult == null) {
            storeResult = new Quaternion();
        }

        if (other.isIdentity()) return storeResult.set(this);

        storeResult.x = other.w * this.x + other.x * this.w + other.y * this.z - other.z * this.y;
        storeResult.y = other.w * this.y + other.y * this.w + other.z * this.x - other.x * this.z;
        storeResult.z = other.w * this.z + other.z * this.w + other.x * this.y - other.y * this.x;
        storeResult.w = other.w * this.w - other.x * this.x - other.y * this.y - other.z * this.z;

        return storeResult;
    }

    /**
     * Multiplies by the argument and returns the (modified) current instance.
     *
     * <p>This method is used to combine rotations. Note that quaternion
     * multiplication is noncommutative, so generally q * p != p * q.
     *
     * @param other the right factor (not null, unaffected unless it's {@code this})
     * @return the (modified) current instance (for chaining)
     */
    public Quaternion multLeftLocal(Quaternion other) {
        if (other.isIdentity()) return this;

        final float newX = other.w * this.x + other.x * this.w + other.y * this.z - other.z * this.y;
        final float newY = other.w * this.y + other.y * this.w + other.z * this.x - other.x * this.z;
        final float newZ = other.w * this.z + other.z * this.w + other.x * this.y - other.y * this.x;
        final float newW = other.w * this.w - other.x * this.x - other.y * this.y - other.z * this.z;
        this.x = newX;
        this.y = newY;
        this.z = newZ;
        this.w = newW;
        return this;
    }

    /**
     * Multiplies by a quaternion with the specified components and returns the
     * (modified) current instance.
     *
     * <p>This method is used to combine rotations. Note that quaternion
     * multiplication is noncommutative, so generally q * p != p * q.
     *
     * @param x the X component of the right factor
     * @param y the Y component of the right factor
     * @param z the Z component of the right factor
     * @param w the W component of the right factor
     * @return the (modified) current instance (for chaining)
     */
    public Quaternion multLeftLocal(float x, float y, float z, float w) {
        if (x == 0 && y == 0 && z == 0 && w == 1) return this;

        final float newX = w * this.x + x * this.w + y * this.z - z * this.y;
        final float newY = w * this.y + y * this.w + z * this.x - x * this.z;
        final float newZ = w * this.z + z * this.w + x * this.y - y * this.x;
        final float newW = w * this.w - x * this.x - y * this.y - z * this.z;
        this.x = newX;
        this.y = newY;
        this.z = newZ;
        this.w = newW;
        return this;
    }

    public Quaternion div(Quaternion other, Quaternion store) {
        if (store == null) {
            store = new Quaternion();
        }

        if (other.isIdentity()) return store.set(this);

        float invNorm = 1.0f / FastMath.fma(other.x, other.x, FastMath.fma(other.y, other.y, FastMath.fma(other.z, other.z, other.w * other.w)));
        float x = -other.x * invNorm;
        float y = -other.y * invNorm;
        float z = -other.z * invNorm;
        float w = other.w * invNorm;
        return store.set(FastMath.fma(this.w, x, FastMath.fma(this.x, w, FastMath.fma(this.y, z, -this.z * y))),
                FastMath.fma(this.w, y, FastMath.fma(-this.x, z, FastMath.fma(this.y, w, this.z * x))),
                FastMath.fma(this.w, z, FastMath.fma(this.x, y, FastMath.fma(-this.y, x, this.z * w))),
                FastMath.fma(this.w, w, FastMath.fma(-this.x, x, FastMath.fma(-this.y, y, -this.z * z))));
    }

    public Quaternion div(Quaternion other) {
        if (other.isIdentity()) return new Quaternion(this);
        return div(other, null);
    }

    public Quaternion divLocal(Quaternion other) {
        if (other.isIdentity()) return this;
        return div(other, this);
    }

    /**
     * Applies the rotation represented by the argument to the current instance.
     *
     * @param matrix the rotation matrix to apply (not null, unaffected)
     */
    public void apply(Matrix3f matrix) {
        float oldX = x, oldY = y, oldZ = z, oldW = w;
        fromRotationMatrix(matrix);
        float tempX = x, tempY = y, tempZ = z, tempW = w;

        x = oldX * tempW + oldY * tempZ - oldZ * tempY + oldW * tempX;
        y = -oldX * tempZ + oldY * tempW + oldZ * tempX + oldW * tempY;
        z = oldX * tempY - oldY * tempX + oldZ * tempW + oldW * tempZ;
        w = -oldX * tempX - oldY * tempY - oldZ * tempZ + oldW * tempW;
    }

    //*************************

    /**
     * Sets the quaternion from the specified orthonormal basis.
     *
     * <p>The 3 basis vectors describe the axes of a rotated coordinate system.
     * They are assumed to be normalized, mutually orthogonal, and in right-hand
     * order. No error checking is performed; the caller must ensure that the
     * specified vectors represent a right-handed coordinate system.
     *
     * @param axis the array of desired basis vectors (not null, array length=3,
     *     each vector having length=1, unaffected)
     * @return the (modified) current instance (for chaining)
     * @throws IllegalArgumentException if {@code axis.length != 3}
     */
    public Quaternion fromAxes(Vector3f[] axis) {
        if (axis.length != 3) {
            throw new IllegalArgumentException(
                    "Axis array must have three elements");
        }
        return fromAxes(axis[0], axis[1], axis[2]);
    }

    /**
     * Sets the quaternion from the specified orthonormal basis.
     *
     * <p>The 3 basis vectors describe the axes of a rotated coordinate system.
     * They are assumed to be normalized, mutually orthogonal, and in right-hand
     * order. No error checking is performed; the caller must ensure that the
     * specified vectors represent a right-handed coordinate system.
     *
     * @param xAxis the X axis of the desired coordinate system (not null,
     *     length=1, unaffected)
     * @param yAxis the Y axis of the desired coordinate system (not null,
     *     length=1, unaffected)
     * @param zAxis the Z axis of the desired coordinate system (not null,
     *     length=1, unaffected)
     * @return the (modified) current instance (for chaining)
     */
    public Quaternion fromAxes(Vector3f xAxis, Vector3f yAxis, Vector3f zAxis) {
        return fromRotationMatrix(xAxis.x, yAxis.x, zAxis.x, xAxis.y, yAxis.y,
                zAxis.y, xAxis.z, yAxis.z, zAxis.z);
    }

    /**
     * Converts the quaternion to a rotated coordinate system and stores the
     * resulting axes in the argument. The current instance is unaffected.
     *
     * <p>The resulting vectors form the basis of a rotated coordinate system.
     * They will be normalized, mutually orthogonal, and in right-hand order.
     *
     * @param axes storage for the results (not null, length=3, each element
     *     non-null, elements modified)
     * @throws IllegalArgumentException if {@code axes.length != 3}
     */
    public void toAxes(Vector3f axes[]) {
        if (axes.length != 3) {
            throw new IllegalArgumentException(
                    "Axes array must have three elements");
        }

        Matrix3f tempMat = toRotationMatrix();
        axes[0] = tempMat.getColumn(0, axes[0]);
        axes[1] = tempMat.getColumn(1, axes[1]);
        axes[2] = tempMat.getColumn(2, axes[2]);
    }

    /**
     * Multiplies with the scalar argument and returns the product as a new
     * instance. The current instance is unaffected.
     *
     * @param scalar the scaling factor
     * @return a new Quaternion
     */
    public Quaternion mult(float scalar) {
        return new Quaternion(scalar * x, scalar * y, scalar * z, scalar * w);
    }

    /**
     * Multiplies by the scalar argument and returns the (modified) current
     * instance.
     *
     * @param scalar the scaling factor
     * @return the (modified) current instance (for chaining)
     */
    public Quaternion multLocal(float scalar) {
        w *= scalar;
        x *= scalar;
        y *= scalar;
        z *= scalar;
        return this;
    }

    /**
     * Returns the dot product with the argument. The current instance is
     * unaffected.
     *
     * <p>This method can be used to quantify the similarity of 2 normalized
     * quaternions.
     *
     * @param q the quaternion to multiply (not null, unaffected)
     * @return the dot product
     */
    public float dot(Quaternion q) {
        return w * q.w + x * q.x + y * q.y + z * q.z;
    }

    public float lengthSquared() {
        return FastMath.fma(x, x, FastMath.fma(y, y, FastMath.fma(z, z, w * w)));
    }

    /**
     * Returns the norm, defined as the dot product of the quaternion with
     * itself. The current instance is unaffected.
     *
     * @return the sum of the squared components (not negative)
     */
    public float norm() {
        return w * w + x * x + y * y + z * z;
    }

    /**
     * Scales the quaternion to have norm=1 and returns the (modified) current
     * instance. For a quaternion with norm=0, the result is undefined.
     *
     * @return the (modified) current instance (for chaining)
     */
    public Quaternion normalize() {
        float n = FastMath.invSqrt(norm());
        return new Quaternion(
                x*n,
                y*n,
                z*n,
                w*n
        );
    }

    /**
     * Scales the quaternion to have norm=1 and returns the (modified) current
     * instance. For a quaternion with norm=0, the result is undefined.
     *
     * @return the (modified) current instance (for chaining)
     */
    public Quaternion normalizeLocal() {
        float n = FastMath.invSqrt(norm());
        x *= n;
        y *= n;
        z *= n;
        w *= n;
        return this;
    }

    /**
     * Returns the multiplicative inverse. For a quaternion with norm=0, null is
     * returned. Either way, the current instance is unaffected.
     *
     * @return a new Quaternion or null
     */
    public Quaternion inverse() {
        float norm = norm();
        if (norm > 0.0) {
            float invNorm = 1.0f / norm;
            return new Quaternion(-x * invNorm, -y * invNorm, -z * invNorm, w * invNorm);
        }
        // return an invalid result to flag the error
        return null;
    }

    /**
     * Inverts the quaternion and returns the (modified) current instance. For
     * a quaternion with norm=0, the current instance is unchanged.
     *
     * @return the current instance (for chaining)
     */
    public Quaternion inverseLocal() {
        float norm = norm();
        if (norm > 0.0) {
            float invNorm = 1.0f / norm;
            x *= -invNorm;
            y *= -invNorm;
            z *= -invNorm;
            w *= invNorm;
        }
        return this;
    }

    /**
     * @return A new quaternion that describes a rotation that would point you
     *         in the exact opposite direction of this Quaternion.
     */
    public Quaternion opposite() {
        return opposite(null);
    }

    /**
     * Returns a rotation with the same axis and the angle increased by 180
     * degrees. If the quaternion isn't normalized, or if the rotation angle is
     * very small, the result is undefined.
     *
     * <p>The current instance is unaffected, unless {@code store} is
     * {@code this}.
     *
     * @param store storage for the result, or null for a new Quaternion
     * @return either {@code store} or a new Quaternion
     */
    public Quaternion opposite(Quaternion store) {
        if (store == null) {
            store = new Quaternion();
        }

        Vector3f axis = new Vector3f();
        float angle = toAngleAxis(axis);

        store.fromAngleAxis(FastMath.PI + angle, axis);
        return store;
    }

    /**
     * Changes the quaternion to a rotation with the same axis and the angle
     * increased by 180 degrees. If the quaternion isn't normalized, or if the
     * rotation angle is very small, the result is undefined.
     *
     * @return the (modified) current instance
     */
    public Quaternion oppositeLocal() {
        return opposite(this);
    }

    public Quaternion conjugate() {
        return conjugate(null);
    }

    public Quaternion conjugateLocal() {
        return conjugate(this);
    }

    public Quaternion conjugate(Quaternion store) {
        if (store == null) {
            store = new Quaternion();
        }

        store.x = -x;
        store.y = -y;
        store.z = -z;
        store.w = w;
        return store;
    }

    public Quaternion conjugateBy(Quaternion q) {
        return conjugateBy(q, null);
    }

    public Quaternion conjugateLocalBy(Quaternion q) {
        return conjugateBy(q, this);
    }

    public Quaternion conjugateBy(Quaternion q, Quaternion store) {
        if (store == null) {
            store = new Quaternion();
        }

        float invNorm = 1.0f / lengthSquared();
        float qix = -q.x * invNorm, qiy = -q.y * invNorm, qiz = -q.z * invNorm, qiw = q.w * invNorm;
        float qpx = FastMath.fma(q.w, x, FastMath.fma(q.x, w, FastMath.fma(q.y, z, -q.z * y)));
        float qpy = FastMath.fma(q.w, y, FastMath.fma(-q.x, z, FastMath.fma(q.y, w, q.z * x)));
        float qpz = FastMath.fma(q.w, z, FastMath.fma(q.x, y, FastMath.fma(-q.y, x, q.z * w)));
        float qpw = FastMath.fma(q.w, w, FastMath.fma(-q.x, x, FastMath.fma(-q.y, y, -q.z * z)));
        return store.set(FastMath.fma(qpw, qix, FastMath.fma(qpx, qiw, FastMath.fma(qpy, qiz, -qpz * qiy))),
                FastMath.fma(qpw, qiy, FastMath.fma(-qpx, qiz, FastMath.fma(qpy, qiw, qpz * qix))),
                FastMath.fma(qpw, qiz, FastMath.fma(qpx, qiy, FastMath.fma(-qpy, qix, qpz * qiw))),
                FastMath.fma(qpw, qiw, FastMath.fma(-qpx, qix, FastMath.fma(-qpy, qiy, -qpz * qiz))));
    }

    /**
     * Negates all 4 components.
     *
     * @deprecated The naming of this method doesn't follow convention. Please
     *     use {@link #negateLocal()} instead.
     */
    public Quaternion negate() {
        return new Quaternion(-x, -y, -z, -w);
    }

    /**
     * Negates all 4 components and returns the (modified) current instance.
     *
     * @return the (modified) current instance (for chaining)
     */
    public Quaternion negateLocal() {
        x = -x;
        y = -y;
        z = -z;
        w = -w;

        return this;
    }

    public double lerp(double delta, double start, double end) {
        return start + delta * (end - start);
    }

    public Vector3f lerp(Vector3f vec1, Vector3f vec2, float delta) {
        return new Vector3f(
                (float) lerp(delta, vec1.x, vec2.x),
                (float) lerp(delta, vec1.y, vec2.y),
                (float) lerp(delta, vec1.z, vec2.z)
        );
    }

    /**
     * Retrieves the right vector, which is the result of transforming a (1,0,0) point
     * with this Quaternion.
     *
     * @return right vector
     */
    public Vector3f rightVector() {
        return new Vector3f(1.0f + 2.0f * (-y*y-z*z), 2.0f * (x*y+z*w), 2.0f * (x*z-y*w));
    }

    /**
     * Retrieves the up vector, which is the result of transforming a (0,1,0) point
     * with this Quaternion.
     *
     * @return up vector
     */
    public Vector3f upVector() {
        return new Vector3f(2.0f * (x*y-z*w), 1.0f + 2.0f * (-x*x-z*z), 2.0f * (y*z+x*w));
    }

    /**
     * Retrieves the forward vector, which is the result of transforming a (0,0,1) point
     * with this Quaternion.
     *
     * @return forward vector
     */
    public Vector3f forwardVector() {
        return new Vector3f(2.0f * (x*z+y*w), 2.0f * (y*z-x*w), 1.0f + 2.0f * (-x*x-y*y));
    }

    public void rotateRollFlip() {
        // rotateZ_unsafe(0.0, 1.0);
        float x = this.x, y = this.y, z = this.z, w = this.w;
        this.x = y;
        this.y = -x;
        this.z = w;
        this.w = -z;
    }

    public void rotateRollDegrees(float angleDegrees) {
        if (angleDegrees != 0.0f) {
            float r = 0.5f * FastMath.toRadians(angleDegrees);
            rotateRoll_unsafe(FastMath.cos(r), FastMath.sin(r));
        }
    }

    /**
     * Rotates the Quaternion an angle around the z-axis
     *
     * @param angle to rotate
     */
    public void rotateRoll(float angle) {
        if (angle != 0.0f) {
            float r = 0.5f * angle;
            rotateRoll_unsafe(FastMath.cos(r), FastMath.sin(r));
        }
    }

    public void rotateRoll(float x, float y) {
        float r = FastMath.halfcosatan2(y, x);
        rotateRoll_unsafe(FastMath.sqrt(0.5f + r), FastMath.sqrt(0.5f - r));
    }

    private void rotateRoll_unsafe(float fx, float fy) {
        float x = this.x * fx + this.y * fy;
        float y = this.y * fx - this.x * fy;
        float z = this.z * fx + this.w * fy;
        float w = this.w * fx - this.z * fy;
        this.x = x; this.y = y; this.z = z; this.w = w;
        this.normalize();
    }

    public void rotateYawFlip() {
        // rotateY_unsafe(0.0, 1.0);
        float x = this.x, y = this.y, z = this.z, w = this.w;
        this.x = -z;
        this.y = w;
        this.z = x;
        this.w = -y;
    }

    public void rotateYaw(float angle) {
        if (angle != 0.0f) {
            float r = 0.5f * angle;
            rotateYaw_unsafe(FastMath.cos(r), FastMath.sin(r));
        }
    }

    public void rotateYawDegrees(float angleDegrees) {
        if (angleDegrees != 0.0f) {
            float r = 0.5f * FastMath.toRadians(angleDegrees);
            rotateYaw_unsafe(FastMath.cos(r), FastMath.sin(r));
        }
    }

    public void rotateYaw(float x, float z) {
        float r = FastMath.halfcosatan2(z, x);
        rotateYaw_unsafe(FastMath.sqrt(0.5f + r), FastMath.sqrt(0.5f - r));
    }

    private void rotateYaw_unsafe(float fx, float fz) {
        float x = this.x * fx - this.z * fz;
        float y = this.y * fx + this.w * fz;
        float z = this.z * fx + this.x * fz;
        float w = this.w * fx - this.y * fz;
        this.x = x; this.y = y; this.z = z; this.w = w;
        this.normalize();
    }

    public void rotatePitchFlip() {
        // rotateX_unsafe(0.0, 1.0);
        float x = this.x, y = this.y, z = this.z, w = this.w;
        this.x = w;
        this.y = z;
        this.z = -y;
        this.w = -x;
    }

    public void rotatePitchDegrees(float angleDegrees) {
        if (angleDegrees != 0.0f) {
            float r = 0.5f * FastMath.toRadians(angleDegrees);
            rotatePitch_unsafe(FastMath.cos(r), FastMath.sin(r));
        }
    }

    public void rotatePitch(float angle) {
        if (angle != 0.0f) {
            float r = 0.5f * angle;
            rotatePitch_unsafe(FastMath.cos(r), FastMath.sin(r));
        }
    }

    public void rotatePitch(float y, float z) {
        float r = FastMath.halfcosatan2(z, y);
        rotatePitch_unsafe(FastMath.sqrt(0.5f + r), FastMath.sqrt(0.5f - r));
    }

    private void rotatePitch_unsafe(float fy, float fz) {
        float x = this.x * fy + this.w * fz;
        float y = this.y * fy + this.z * fz;
        float z = this.z * fy - this.y * fz;
        float w = this.w * fy - this.x * fz;
        this.x = x; this.y = y; this.z = z; this.w = w;
        this.normalize();
    }

    public void rotatePitchYawRoll(Vector3f rotation) {
        rotatePitchYawRoll(rotation.x, rotation.y, rotation.z);
    }

    public void rotatePitchYawRoll(Vector rotation) {
        rotatePitchYawRoll((float) rotation.getX(), (float) rotation.getY(), (float) rotation.getZ());
    }

    public void rotatePitchYawRoll(float pitch, float yaw, float roll) {
        this.rotatePitch(pitch);
        this.rotateYaw(-yaw);
        this.rotateRoll(roll);
    }

    public void rotatePitchYawRollDegrees(float pitch, float yaw, float roll) {
        this.rotatePitch(FastMath.toRadians(pitch));
        this.rotateYaw(-FastMath.toRadians(yaw));
        this.rotateRoll(FastMath.toRadians(roll));
    }

    public Quaternion setYaw(float yaw) {
        rotateYaw(yaw-getYaw());
        return this;
    }

    public Quaternion setPitch(float pitch) {
        rotatePitch(pitch-getPitch());
        return this;
    }

    public Quaternion setRoll(float roll) {
        rotateRoll(roll-getRoll());
        return this;
    }

    public Quaternion setYawDegrees(float yaw) {
        rotateYaw(FastMath.toRadians(yaw)-getYaw());
        return this;
    }

    public Quaternion setPitchDegrees(float pitch) {
        rotatePitch(FastMath.toRadians(pitch)-getPitch());
        return this;
    }

    public Quaternion setRollDegrees(float roll) {
        rotateRoll(FastMath.toRadians(roll)-getRoll());
        return this;
    }

    public float getYaw() {
        final float test = 2.0f * (w * x - y * z);
        if (Math.abs(test) < (1.0f - 1E-15)) {
            float yaw = CommonTrigMath.atan2(-2.0f * (w * y + z * x), 1.0f - 2.0f * (x * x + y * y));
            float roll_x = 0.5f - (x * x + z * z);
            if (roll_x <= 0.0f && (FastMath.abs((w * z + x * y)) > roll_x)) {
                yaw += (yaw < 0.0f) ? FastMath.PI : -FastMath.PI;
            }
            return -yaw;
        } else if (test < 0.0) {
            return -(-2.0f * CommonTrigMath.atan2(z, w));
        } else {
            return -(2.0f * CommonTrigMath.atan2(z, w));
        }
    }

    public float getYawDegrees() {
        final float test = 2.0f * (w * x - y * z);
        if (Math.abs(test) < (1.0f - 1E-15)) {
            float yaw = CommonTrigMath.atan2(-2.0f * (w * y + z * x), 1.0f - 2.0f * (x * x + y * y));
            float roll_x = 0.5f - (x * x + z * z);
            if (roll_x <= 0.0f && (FastMath.abs((w * z + x * y)) > roll_x)) {
                yaw += (yaw < 0.0f) ? FastMath.PI : -FastMath.PI;
            }
            return -FastMath.toDegrees(yaw);
        } else if (test < 0.0) {
            return -FastMath.toDegrees(-2.0f * CommonTrigMath.atan2(z, w));
        } else {
            return -FastMath.toDegrees(2.0f * CommonTrigMath.atan2(z, w));
        }
    }

    public float getPitch() {
        final float test = 2.0f * (w * x - y * z);
        if (FastMath.abs(test) < (1.0 - 1E-15)) {
            float pitch = FastMath.asin(test);
            float roll_x = 0.5f - (x * x + z * z);
            if (roll_x <= 0.0 && (FastMath.abs((w * z + x * y)) > roll_x)) {
                pitch = -pitch;
                pitch += (pitch < 0.0) ? FastMath.PI : -FastMath.PI;
            }
            return pitch;
        } else if (test < 0.0) {
            return -FastMath.TWO_PI;
        } else {
            return FastMath.TWO_PI;
        }
    }
    public float getPitchDegrees() {
        final float test = 2.0f * (w * x - y * z);
        if (FastMath.abs(test) < (1.0 - 1E-15)) {
            float pitch = FastMath.asin(test);
            float roll_x = 0.5f - (x * x + z * z);
            if (roll_x <= 0.0 && (FastMath.abs((w * z + x * y)) > roll_x)) {
                pitch = -pitch;
                pitch += (pitch < 0.0) ? FastMath.PI : -FastMath.PI;
            }
            return FastMath.toDegrees(pitch);
        } else if (test < 0.0) {
            return -90.0f;
        } else {
            return 90.0f;
        }
    }

    public float getRoll() {
        final float test = 2.0f * (w * x - y * z);
        if (FastMath.abs(test) < (1.0f - 1E-15)) {
            float roll = CommonTrigMath.atan2(2.0f * (w * z + x * y), 1.0f - 2.0f * (x * x + z * z));
            if (FastMath.abs(roll) > (0.5 * FastMath.PI)) {
                roll += (roll < 0.0f) ? FastMath.PI : -FastMath.PI;
            }
            return roll;
        } else {
            return 0.0f;
        }
    }

    public float getRollDegrees() {
        final float test = 2.0f * (w * x - y * z);
        if (FastMath.abs(test) < (1.0f - 1E-15)) {
            float roll = CommonTrigMath.atan2(2.0f * (w * z + x * y), 1.0f - 2.0f * (x * x + z * z));
            if (FastMath.abs(roll) > (0.5 * FastMath.PI)) {
                roll += (roll < 0.0f) ? FastMath.PI : -FastMath.PI;
            }
            return FastMath.toDegrees(roll);
        } else {
            return 0.0f;
        }
    }

    public Vector3f toPitchYawRoll() {
        final float test = 2.0f * (w * x - y * z);

        if (Math.abs(test) < (1.0f - 1E-15)) {
            // Standard angle
            float roll = CommonTrigMath.atan2(2.0f * (w * z + x * y), 1.0f - 2.0f * (x * x + z * z));
            float pitch = FastMath.asin(test);
            float yaw = CommonTrigMath.atan2(-2.0f * (w * y + z * x), 1.0f - 2.0f * (x * x + y * y));

            if (Math.abs(roll) > (0.5 * FastMath.PI)) {
                roll += (roll < 0.0) ? FastMath.PI : -FastMath.PI;
                yaw += (yaw < 0.0) ? FastMath.PI : -FastMath.PI;
                pitch = -pitch;
                pitch += (pitch < 0.0) ? FastMath.PI : -FastMath.PI;
            }

            return new Vector3f(pitch, yaw, roll);

        } else if (test < 0.0) {
            // This is at the pitch=-90.0 singularity
            // All we can do is yaw (or roll) around the vertical axis
            return new Vector3f(-FastMath.TWO_PI, -2.0f * CommonTrigMath.atan2(z, w), 0.0f);

        } else {
            // This is at the pitch=90.0 singularity
            // All we can do is yaw (or roll) around the vertical axis
            return new Vector3f(FastMath.TWO_PI, 2.0f * CommonTrigMath.atan2(z, w), 0.0f);
        }
    }

    public Vector3f toPitchYawRollDegrees() {
        final float test = 2.0f * (w * x - y * z);

        if (Math.abs(test) < (1.0f - 1E-15)) {
            // Standard angle
            float roll = CommonTrigMath.atan2(2.0f * (w * z + x * y), 1.0f - 2.0f * (x * x + z * z));
            float pitch = FastMath.asin(test);
            float yaw = CommonTrigMath.atan2(-2.0f * (w * y + z * x), 1.0f - 2.0f * (x * x + y * y));

            if (Math.abs(roll) > (0.5 * FastMath.PI)) {
                roll += (roll < 0.0) ? FastMath.PI : -FastMath.PI;
                yaw += (yaw < 0.0) ? FastMath.PI : -FastMath.PI;
                pitch = -pitch;
                pitch += (pitch < 0.0) ? FastMath.PI : -FastMath.PI;
            }

            return new Vector3f(FastMath.toDegrees(pitch), FastMath.toDegrees(yaw), FastMath.toDegrees(roll));

        } else if (test < 0.0) {
            // This is at the pitch=-90.0 singularity
            // All we can do is yaw (or roll) around the vertical axis
            return new Vector3f(-90.0f, FastMath.toDegrees(-2.0f * CommonTrigMath.atan2(z, w)), 0.0f);

        } else {
            // This is at the pitch=90.0 singularity
            // All we can do is yaw (or roll) around the vertical axis
            return new Vector3f(90.0f, FastMath.toDegrees(2.0f * CommonTrigMath.atan2(z, w)), 0.0f);
        }
    }

    public Quaternion fromPitchYawRoll(float pitch, float yaw, float roll) {
        this.rotatePitch(pitch);
        this.rotateYaw(-yaw);
        this.rotateRoll(roll);
        return this;
    }

    public Quaternion fromPitchYawRollDegrees(float pitch, float yaw, float roll) {
        this.rotatePitch(FastMath.toRadians(pitch));
        this.rotateYaw(-FastMath.toRadians(yaw));
        this.rotateRoll(FastMath.toRadians(roll));
        return this;
    }


    public Quaternion fromMinecraftEulerAngle(EulerAngle angle) {
        this.rotatePitch((float) angle.getX());
        this.rotateYaw((float) angle.getY());
        this.rotateRoll((float) angle.getZ());
        return this;
    }


    public EulerAngle toMinecraftEulerAngle() {
        Vector3f axis1 = Vector3f.UNIT_Z;
        Vector3f axis2 = Vector3f.UNIT_Y;
        Vector3f axis3 = Vector3f.UNIT_X;

        Vector3f vec3 = axis3.apply(this);
        Vector3f invVec1 = axis1.apply(inverse());

        float angle2Sin = vec3.dot(axis2.cross(axis3));

        if (angle2Sin < -FastMath.AXIS_ANGLE_SINGULARITY_THRESHOLD || angle2Sin > FastMath.AXIS_ANGLE_SINGULARITY_THRESHOLD) {
            Vector3f vec2 = axis2.apply(this);

            float angle1TanY = vec2.dot(axis1.cross(axis2));
            float angle1TanX = vec2.dot(axis2);

            float angle2 = angle2Sin > FastMath.AXIS_ANGLE_SINGULARITY_THRESHOLD ?
                    FastMath.HALF_PI :
                    -FastMath.HALF_PI;

            return new EulerAngle(
                    0.0D,
                    -angle2,
                    -FastMath.atan2(angle1TanY, angle1TanX)
            );
        }

        Vector3f  crossAxis13 = axis1.cross(axis3);

        float angle1TanY = vec3.dot(crossAxis13);
        float angle1TanX = vec3.dot(axis3);

        float angle3TanY = invVec1.dot(crossAxis13);
        float angle3TanX = invVec1.dot(axis1);

        return new EulerAngle(
                FastMath.atan2(angle3TanY, angle3TanX),
                -FastMath.asin(angle2Sin),
                -FastMath.atan2(angle1TanY, angle1TanX)
        );
    }

    public EulerAngle toMinecraftEulerAngleDegrees() {
        Vector3f axis1 = Vector3f.UNIT_Z;
        Vector3f axis2 = Vector3f.UNIT_Y;
        Vector3f axis3 = Vector3f.UNIT_X;

        Vector3f vec3 = axis3.apply(this);
        Vector3f invVec1 = axis1.apply(inverse());

        float angle2Sin = vec3.dot(axis2.cross(axis3));

        if (angle2Sin < -FastMath.AXIS_ANGLE_SINGULARITY_THRESHOLD || angle2Sin > FastMath.AXIS_ANGLE_SINGULARITY_THRESHOLD) {
            Vector3f vec2 = axis2.apply(this);

            float angle1TanY = vec2.dot(axis1.cross(axis2));
            float angle1TanX = vec2.dot(axis2);

            float angle2 = angle2Sin > FastMath.AXIS_ANGLE_SINGULARITY_THRESHOLD ?
                    FastMath.HALF_PI :
                    -FastMath.HALF_PI;

            return new EulerAngle(
                    0.0D,
                    FastMath.toDegrees(-angle2),
                    FastMath.toDegrees(-FastMath.atan2(angle1TanY, angle1TanX))
            );
        }

        Vector3f  crossAxis13 = axis1.cross(axis3);

        float angle1TanY = vec3.dot(crossAxis13);
        float angle1TanX = vec3.dot(axis3);

        float angle3TanY = invVec1.dot(crossAxis13);
        float angle3TanX = invVec1.dot(axis1);

        return new EulerAngle(
                FastMath.toDegrees(FastMath.atan2(angle3TanY, angle3TanX)),
                FastMath.toDegrees(-FastMath.asin(angle2Sin)),
                FastMath.toDegrees(-FastMath.atan2(angle1TanY, angle1TanX))
        );
    }

    public Vector3f toEulerAngle() {
        Vector3f axis1 = Vector3f.UNIT_Z;
        Vector3f axis2 = Vector3f.UNIT_Y;
        Vector3f axis3 = Vector3f.UNIT_X;

        Vector3f vec3 = axis3.apply(this);
        Vector3f invVec1 = axis1.apply(inverse());

        float angle2Sin = vec3.dot(axis2.cross(axis3));

        if (angle2Sin < -FastMath.AXIS_ANGLE_SINGULARITY_THRESHOLD || angle2Sin > FastMath.AXIS_ANGLE_SINGULARITY_THRESHOLD) {
            Vector3f vec2 = axis2.apply(this);

            float angle1TanY = vec2.dot(axis1.cross(axis2));
            float angle1TanX = vec2.dot(axis2);

            float angle2 = angle2Sin > FastMath.AXIS_ANGLE_SINGULARITY_THRESHOLD ?
                    FastMath.HALF_PI :
                    -FastMath.HALF_PI;

            return new Vector3f(
                    0.0f,
                    -angle2,
                    -FastMath.atan2(angle1TanY, angle1TanX)
            );
        }

        Vector3f  crossAxis13 = axis1.cross(axis3);

        float angle1TanY = vec3.dot(crossAxis13);
        float angle1TanX = vec3.dot(axis3);

        float angle3TanY = invVec1.dot(crossAxis13);
        float angle3TanX = invVec1.dot(axis1);

        return new Vector3f(
                FastMath.atan2(angle3TanY, angle3TanX),
                -FastMath.asin(angle2Sin),
                -FastMath.atan2(angle1TanY, angle1TanX)
        );
    }

    public Vector3f toEulerAngleDegrees() {
        Vector3f axis1 = Vector3f.UNIT_Z;
        Vector3f axis2 = Vector3f.UNIT_Y;
        Vector3f axis3 = Vector3f.UNIT_X;

        Vector3f vec3 = axis3.apply(this);
        Vector3f invVec1 = axis1.apply(inverse());

        float angle2Sin = vec3.dot(axis2.cross(axis3));

        if (angle2Sin < -FastMath.AXIS_ANGLE_SINGULARITY_THRESHOLD || angle2Sin > FastMath.AXIS_ANGLE_SINGULARITY_THRESHOLD) {
            Vector3f vec2 = axis2.apply(this);

            float angle1TanY = vec2.dot(axis1.cross(axis2));
            float angle1TanX = vec2.dot(axis2);

            float angle2 = angle2Sin > FastMath.AXIS_ANGLE_SINGULARITY_THRESHOLD ?
                    FastMath.HALF_PI :
                    -FastMath.HALF_PI;

            return new Vector3f(
                    0.0f,
                    FastMath.toDegrees(-angle2),
                    FastMath.toDegrees(-FastMath.atan2(angle1TanY, angle1TanX))
            );
        }

        Vector3f  crossAxis13 = axis1.cross(axis3);

        float angle1TanY = vec3.dot(crossAxis13);
        float angle1TanX = vec3.dot(axis3);

        float angle3TanY = invVec1.dot(crossAxis13);
        float angle3TanX = invVec1.dot(axis1);

        return new Vector3f(
                FastMath.toDegrees(FastMath.atan2(angle3TanY, angle3TanX)),
                FastMath.toDegrees(-FastMath.asin(angle2Sin)),
                FastMath.toDegrees(-FastMath.atan2(angle1TanY, angle1TanX))
        );
    }

    /**
     * Produces an average rotation from several different rotation values.
     * If only one rotation value is specified, then that one value is returned.
     * If no rotation values are specified, identity is returned.
     * The returned Quaternion is always a copy.
     *
     * @param values Iterable of Quaternion rotation values
     * @return average rotation Quaternion
     */
    public static Quaternion average(Iterable<Quaternion> values) {
        Iterator<Quaternion> iter = values.iterator();

        // No values, return identity
        if (!iter.hasNext()) {
            return new Quaternion();
        }

        // Only one value, return the one value (make sure to clone!)
        Quaternion first = iter.next();
        if (!iter.hasNext()) {
            return first.clone();
        }

        // Build up an average
        int num_values = 1;
        Quaternion result = first.clone();
        do {
            Quaternion next = iter.next();
            if (first.dot(next) >= 0.0f) {
                result.x += next.x;
                result.y += next.y;
                result.z += next.z;
                result.w += next.w;
            } else {
                result.x -= next.x;
                result.y -= next.y;
                result.z -= next.z;
                result.w -= next.w;
            }
            num_values++;
        } while (iter.hasNext());

        // Divide by the number of values, then normalize the result
        double fact = 1.0 / (double) num_values;
        result.x *= fact;
        result.y *= fact;
        result.z *= fact;
        result.w *= fact;
        result.normalizeLocal();
        return result;
    }

    /**
     * Returns a string representation of the quaternion, which is unaffected.
     * For example, the identity quaternion is represented by:
     * <pre>
     * (0.0, 0.0, 0.0, 1.0)
     * </pre>
     *
     * @return the string representation (not null, not empty)
     */
    @Override
    public String toString() {
        return "(x: " + x + ", y: " + y + ", z: " + z + ", w: " + w + ")";
    }

    public String debug() {
        StringBuilder result = new StringBuilder("Quaternion\n");
        result.append("Values: (x: " + x + ", y: " + y + ", z: " + z + ", w: " + w + ")");
        result.append("\n");
        result.append("Pitch/Yaw/Roll: (pitch: "+FastMath.toDegrees(getPitch()) + ", yaw: " + FastMath.toDegrees(getYaw()) + ", roll: " + FastMath.toDegrees(getRoll())+")");
        result.append("\n");
        return result.toString();
    }

    /**
     * Tests for exact equality with the argument, distinguishing -0 from 0. If
     * {@code o} is null, false is returned. Either way, the current instance is
     * unaffected.
     *
     * @param o the object to compare (may be null, unaffected)
     * @return true if {@code this} and {@code o} have identical values,
     *     otherwise false
     */
    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Quaternion)) {
            return false;
        }

        if (this == o) {
            return true;
        }

        Quaternion comp = (Quaternion) o;
        if (Float.compare(x, comp.x) != 0) {
            return false;
        }
        if (Float.compare(y, comp.y) != 0) {
            return false;
        }
        if (Float.compare(z, comp.z) != 0) {
            return false;
        }
        if (Float.compare(w, comp.w) != 0) {
            return false;
        }
        return true;
    }

    /**
     * Returns a hash code. If two quaternions have identical values, they
     * will have the same hash code. The current instance is unaffected.
     *
     * @return a 32-bit value for use in hashing
     * @see Object#hashCode()
     */
    @Override
    public int hashCode() {
        int hash = 37;
        hash = 37 * hash + Float.floatToIntBits(x);
        hash = 37 * hash + Float.floatToIntBits(y);
        hash = 37 * hash + Float.floatToIntBits(z);
        hash = 37 * hash + Float.floatToIntBits(w);
        return hash;

    }

    /**
     * Sets the quaternion from an {@code ObjectInput} object.
     *
     * <p>Used with serialization. Should not be invoked directly by application
     * code.
     *
     * @param in the object to read from (not null)
     * @throws IOException if the ObjectInput cannot read a float
     * @see java.io.Externalizable
     */
    public void readExternal(ObjectInput in) throws IOException {
        x = in.readFloat();
        y = in.readFloat();
        z = in.readFloat();
        w = in.readFloat();
    }

    /**
     * Writes the quaternion to an {@code ObjectOutput} object.
     *
     * <p>Used with serialization. Should not be invoked directly by application
     * code.
     *
     * @param out the object to write to (not null)
     * @throws IOException if the ObjectOutput cannot write a float
     * @see java.io.Externalizable
     */
    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeFloat(x);
        out.writeFloat(y);
        out.writeFloat(z);
        out.writeFloat(w);
    }

    /**
     * Creates a copy. The current instance is unaffected.
     *
     * @return a new instance, equivalent to the current one
     */
    @Override
    public Quaternion clone() {
        try {
            return (Quaternion) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new AssertionError(); // can not happen
        }
    }

    // ************************************************
    // Fork code

    /**
     * Умножение кватерниона на вектор (Vector3f).
     * Возвращает получившийся вектор.
     * Учитывайте, что для jMonkeyEngine есть своя реализация,
     * здесь лишь общий принцип.
     */
    public Vector3f multLocal(Vector3f v) {
        // Copy the original components, so we don't overwrite them prematurely
        float vx = v.x;
        float vy = v.y;
        float vz = v.z;

        // Calculate the cross terms (q.xyz x v)
        float crossX =  y * vz - z * vy;
        float crossY =  z * vx - x * vz;
        float crossZ =  x * vy - y * vx;

        // Scale them by the quaternion's w and by 2
        float w2 = w + w;  // equivalent to 2 * w
        float cxwX = w2 * crossX;
        float cxwY = w2 * crossY;
        float cxwZ = w2 * crossZ;

        // Add (q.xyz x v)*2w to v
        v.x += cxwX;
        v.y += cxwY;
        v.z += cxwZ;

        // Now add 2*(q.xyz x (q.xyz x v)) in place (the cross of crossX, crossY, crossZ with q.xyz)
        float crossCrossX = (y * crossZ) - (z * crossY);
        float crossCrossY = (z * crossX) - (x * crossZ);
        float crossCrossZ = (x * crossY) - (y * crossX);

        v.x += crossCrossX + crossCrossX;
        v.y += crossCrossY + crossCrossY;
        v.z += crossCrossZ + crossCrossZ;

        return v;
    }

    public float getRoll0() {
        // This is a test for singularities
        float test = x * y + z * w;

        // Special case for north pole
        if (test > SINGULARITY_NORTH_POLE)
            return 0;

        // Special case for south pole
        if (test < SINGULARITY_SOUTH_POLE)
            return 0;

        return FastMath.atan2(
                2 * x * w - 2 * y * z,
                1 - 2 * x * x - 2 * z * z
        );
    }

    public float getPitch0() {
        // This is a test for singularities
        float test = x * y + z * w;

        // Special case for north pole
        if (test > SINGULARITY_NORTH_POLE)
            return (FastMath.HALF_PI);

        // Special case for south pole
        if (test < SINGULARITY_SOUTH_POLE)
            return (-FastMath.HALF_PI);

        return FastMath.asin(2 * test);
    }

    public float getYaw0() {
        // This is a test for singularities
        float test = x * y + z * w;

        // Special case for north pole
        if (test > SINGULARITY_NORTH_POLE)
            return (2 * FastMath.atan2(x, w));

        // Special case for south pole
        if (test < SINGULARITY_SOUTH_POLE)
            return (-2 * FastMath.atan2(x, w));

        return FastMath.atan2(
                2 * y * w - 2 * x * z,
                1 - 2 * y * y - 2 * z * z
        );
    }

    public Quaternion setYaw0(float yaw) {
        return this.fromAngles(getRoll0(), yaw, getPitch0());
    }

    public Quaternion setPitch0(float pitch) {
        return this.fromAngles(getRoll0(), getYaw0(), pitch);
    }

    public Quaternion setRoll0(float roll) {
        return this.fromAngles(roll, getYaw0(), getPitch0());
    }

    // Experimental new rotation methods
    public Quaternion resetYaw() {
        rotator.fromAngles(0.0f, -getYaw(), 0.0f);
        this.multLeftLocal(rotator);
        return this;
    }

    public Quaternion resetPitch() {
        rotator.fromAngles(0.0f, 0.0f, -getPitch());
        this.multLeftLocal(rotator);
        return this;
    }

    public Quaternion resetRoll() {
        rotator.fromAngles(-getRoll(), 0.0f, 0.0f);
        this.multLeftLocal(rotator);
        return this;
    }
}