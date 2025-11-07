/*
 * Copyright (c) 2009-2024 jMonkeyEngine
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

import jme3utilities.Validate;

/**
 * <code>FastMath</code> provides 'fast' math approximations and float equivalents of Math
 * functions.  These are all used as static values and functions.
 *
 * @author Various
 * @version $Id: FastMath.java,v 1.45 2007/08/26 08:44:20 irrisor Exp $
 */
final public class FastMath {
    private FastMath() {
    }
    /**
     * A "close to zero" double epsilon value for use
     */
    public static final float FLT_EPSILON = 1.1920928955078125E-7f;
    /**
     * The value PI as a float. (180 degrees)
     */
    public static final float PI = (float) Math.PI;
    /**
     * The value 2PI as a float. (360 degrees)
     */
    public static final float TWO_PI = 2.0f * PI;
    /**
     * The value PI/2 as a float. (90 degrees)
     */
    public static final float HALF_PI = 0.5f * PI;
    /**
     * The value PI/4 as a float. (45 degrees)
     */
    public static final float QUARTER_PI = 0.25f * PI;
    /**
     * A value to multiply a degree value by, to convert it to radians.
     */
    public static final float DEG_TO_RAD = PI / 180.0f;
    /**
     * A value to multiply a radian value by, to convert it to degrees.
     */
    public static final float RAD_TO_DEG = 180.0f / PI;

    /**
     * Returns the arc tangent of an angle given in radians.<br>
     *
     * @param fValue The angle, in radians.
     * @return fValue's atan
     * @see Math#atan(double)
     */
    public static float atan(float fValue) {
        return (float) Math.atan(fValue);
    }

    /**
     * A direct call to Math.atan2.
     *
     * @param fY ordinate
     * @param fX abscissa
     * @return Math.atan2(fY,fX)
     * @see Math#atan2(double, double)
     */
    public static float atan2(float fY, float fX) {
        return (float) Math.atan2(fY, fX);
    }

    /**
     * Returns cosine of an angle. Direct call to java.lang.Math
     *
     * @see Math#cos(double)
     * @param v The angle to cosine.
     * @return the cosine of the angle.
     */
    public static float cos(float v) {
        return (float) Math.cos(v);
    }

    /**
     * Returns the sine of an angle. Direct call to java.lang.Math
     *
     * @see Math#sin(double)
     * @param v The angle to sine.
     * @return the sine of the angle.
     */
    public static float sin(float v) {
        return (float) Math.sin(v);
    }

    /**
     * Returns Absolute value of a float.
     *
     * @param fValue The value to abs.
     * @return The abs of the value.
     * @see Math#abs(float)
     */
    public static float abs(float fValue) {
        if (fValue < 0) {
            return -fValue;
        }
        return fValue;
    }

    /**
     * Returns a number raised to an exponent power. fBase^fExponent
     *
     * @param fBase The base value (IE 2)
     * @param fExponent The exponent value (IE 3)
     * @return base raised to exponent (IE 8)
     * @see Math#pow(double, double)
     */
    public static float pow(float fBase, float fExponent) {
        return (float) Math.pow(fBase, fExponent);
    }

    /**
     * Returns the square root of a given value.
     *
     * @param fValue The value to sqrt.
     * @return The square root of the given value.
     * @see Math#sqrt(double)
     */
    public static float sqrt(float fValue) {
        return (float) Math.sqrt(fValue);
    }

    /**
     * Returns the tangent of the specified angle.
     *
     * @param fValue The value to tangent, in radians.
     * @return The tangent of fValue.
     * @see Math#tan(double)
     */
    public static float tan(float fValue) {
        return (float) Math.tan(fValue);
    }

    /**
     * Take a float input and clamp it between min and max.
     *
     * @param input the value to be clamped
     * @param min the minimum output value
     * @param max the maximum output value
     * @return clamped input
     */
    public static float clamp(float input, float min, float max) {
        return (input < min) ? min : (input > max) ? max : input;
    }

    /**
     * Determine if two floats are approximately equal.
     * This takes into account the magnitude of the floats, since
     * large numbers will have larger differences be close to each other.
     *
     * Should return true for a=100000, b=100001, but false for a=10000, b=10001.
     *
     * @param a The first float to compare
     * @param b The second float to compare
     * @return True if a and b are approximately equal, false otherwise.
     */
    public static boolean approximateEquals(float a, float b) {
        if (a == b) {
            return true;
        } else {
            return (abs(a - b) / Math.max(abs(a), abs(b))) <= 0.00001f;
        }
    }



    // *************************************************************************
    // fork code
    public static final float AXIS_ANGLE_SINGULARITY_THRESHOLD = 0.9999999999F;
    public static final float KPH_TO_MPS = 0.277778f;
    public static final float MPS_TO_KPH = 3.6f;
    public static final float KPH_TO_MHP = 0.621371192f;
    public static final float MHP_TO_KPH = 1.609344f;

    public static final float HP_TO_W = 745f;
    public static final float W_TO_HP = 0.001341f;


    public static final float ZERO_TOLERANCE = 0.0001f;


    public static float acos(float fValue) {
        if (-1.0f < fValue) {
            if (fValue < 1.0f) {
                return (float) Math.acos(fValue);
            }

            return 0.0f;
        }

        return PI;
    }

    public static float asin(float fValue) {
        if (-1.0f < fValue) {
            if (fValue < 1.0f) {
                return (float) Math.asin(fValue);
            }

            return HALF_PI;
        }

        return -HALF_PI;
    }

    public static float safeAsin(float r) {
        return r <= -1.0F ? -1.5707964F : (r >= 1.0F ? 1.5707964F : (float)Math.asin(r));
    }

    public static float halfcosatan2(float y, float x) {
        float tmp = y / x;
        tmp *= tmp;
        tmp += 1.0f;
        return ((x < 0.0f) ? -0.5f : 0.5f) / FastMath.sqrt(tmp);
    }

    public static float invSqrt(float fValue) {
        return (float) (1.0f / Math.sqrt(fValue));
    }

    public static float fma(float a, float b, float c) {
        return a * b + c;
    }

    public static float cosFromSin(float sin, float angle) {
        return sin(angle + 1.5707964F);
    }

    public static float toRadians(float angdeg) {
        return angdeg * DEG_TO_RAD;
    }

    public static float toDegrees(float angrad) {
        return angrad * RAD_TO_DEG;
    }

    public static float toWATTS(float hp) {
        return hp * HP_TO_W;
    }

    public static float toHP(float watts) {
        return watts * W_TO_HP;
    }

    public static float lerp(float delta, float start, float end) {
        return start + delta * (end - start);
    }

    public static double circle(double abscissa) {
        assert Validate.inRange(abscissa, "abscissa", -1.0, 1.0);

        double y = Math.sqrt(1.0 - abscissa * abscissa);

        assert y >= 0.0 : y;
        assert y <= 1.0 : y;
        return y;
    }

    public static float circle(float abscissa) {
        assert Validate.inRange(abscissa, "abscissa", -1f, 1f);

        double x = abscissa;
        float y = (float) circle(x);

        assert y >= 0f : y;
        assert y <= 1f : y;
        return y;
    }
}