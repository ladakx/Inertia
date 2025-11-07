package com.jme3.math;

public class SplineMath {

    /**
     * Linear interpolation from startValue to endValue by the given percent.
     * Basically: ((1 - percent) * startValue) + (percent * endValue)
     *
     * @param scale
     *            scale value to use. if 1, use endValue, if 0, use startValue.
     * @param startValue
     *            Beginning value. 0% of f
     * @param endValue
     *            ending value. 100% of f
     * @return The interpolated value between startValue and endValue.
     */
    public static float interpolateLinear(float scale, float startValue, float endValue) {
        if (startValue == endValue) {
            return startValue;
        }
        if (scale <= 0f) {
            return startValue;
        }
        if (scale >= 1f) {
            return endValue;
        }
        return ((1f - scale) * startValue) + (scale * endValue);
    }

    /**
     * Linear interpolation from startValue to endValue by the given percent.
     * Basically: ((1 - percent) * startValue) + (percent * endValue)
     *
     * @param scale
     *            scale value to use. if 1, use endValue, if 0, use startValue.
     * @param startValue
     *            Beginning value. 0% of f
     * @param endValue
     *            ending value. 100% of f
     * @param store a vector3f to store the result
     * @return The interpolated value between startValue and endValue.
     */
    public static Vector3f interpolateLinear(float scale, Vector3f startValue,
                                             Vector3f endValue, Vector3f store) {
        if (store == null) {
            store = new Vector3f();
        }
        store.x = interpolateLinear(scale, startValue.x, endValue.x);
        store.y = interpolateLinear(scale, startValue.y, endValue.y);
        store.z = interpolateLinear(scale, startValue.z, endValue.z);
        return store;
    }

    /**
     * Linear interpolation from startValue to endValue by the given percent.
     * Basically: ((1 - percent) * startValue) + (percent * endValue)
     *
     * @param scale
     *            scale value to use. if 1, use endValue, if 0, use startValue.
     * @param startValue
     *            Beginning value. 0% of f
     * @param endValue
     *            ending value. 100% of f
     * @return The interpolated value between startValue and endValue.
     */
    public static Vector3f interpolateLinear(float scale, Vector3f startValue, Vector3f endValue) {
        return interpolateLinear(scale, startValue, endValue, null);
    }

    /**
     * Linear extrapolation from startValue to endValue by the given scale.
     * if scale is between 0 and 1 this method returns the same result as interpolateLinear
     * if the scale is over 1 the value is linearly extrapolated.
     * Note that the end value is the value for a scale of 1.
     *
     * @param scale the scale for extrapolation
     * @param startValue the starting value (scale = 0)
     * @param endValue the end value (scale = 1)
     * @return an extrapolation for the given parameters
     */
    public static float extrapolateLinear(float scale, float startValue, float endValue) {
//        if (scale <= 0f) {
//            return startValue;
//        }
        return ((1f - scale) * startValue) + (scale * endValue);
    }

    /**
     * Linear extrapolation from startValue to endValue by the given scale.
     * if scale is between 0 and 1 this method returns the same result as interpolateLinear
     * if the scale is over 1 the value is linearly extrapolated.
     * Note that the end value is the value for a scale of 1.
     *
     * @param scale the scale for extrapolation
     * @param startValue the starting value (scale = 0)
     * @param endValue the end value (scale = 1)
     * @param store an initialized vector to store the return value
     * @return an extrapolation for the given parameters
     */
    public static Vector3f extrapolateLinear(float scale, Vector3f startValue,
                                             Vector3f endValue, Vector3f store) {
        if (store == null) {
            store = new Vector3f();
        }
//        if (scale <= 1f) {
//            return interpolateLinear(scale, startValue, endValue, store);
//        }
        store.x = extrapolateLinear(scale, startValue.x, endValue.x);
        store.y = extrapolateLinear(scale, startValue.y, endValue.y);
        store.z = extrapolateLinear(scale, startValue.z, endValue.z);
        return store;
    }

    /**
     * Linear extrapolation from startValue to endValue by the given scale.
     * if scale is between 0 and 1 this method returns the same result as interpolateLinear
     * if the scale is over 1 the value is linearly extrapolated.
     * Note that the end value is the value for a scale of 1.
     *
     * @param scale the scale for extrapolation
     * @param startValue the starting value (scale = 0)
     * @param endValue the end value (scale = 1)
     * @return an extrapolation for the given parameters
     */
    public static Vector3f extrapolateLinear(float scale, Vector3f startValue, Vector3f endValue) {
        return extrapolateLinear(scale, startValue, endValue, null);
    }

    /**
     * Interpolate a spline between at least 4 control points following the Catmull-Rom equation.
     * here is the interpolation matrix
     * m = [ 0.0  1.0  0.0   0.0 ]
     *     [-T    0.0  T     0.0 ]
     *     [ 2T   T-3  3-2T  -T  ]
     *     [-T    2-T  T-2   T   ]
     * where T is the curve tension
     * the result is a value between p1 and p2, t=0 for p1, t=1 for p2
     *
     * @param u value from 0 to 1
     * @param T The tension of the curve
     * @param p0 control point 0
     * @param p1 control point 1
     * @param p2 control point 2
     * @param p3 control point 3
     * @return Catmull–Rom interpolation
     */
    public static float interpolateCatmullRom(float u, float T, float p0, float p1, float p2, float p3) {
        float c1, c2, c3, c4;
        c1 = p1;
        c2 = -1.0f * T * p0 + T * p2;
        c3 = 2 * T * p0 + (T - 3) * p1 + (3 - 2 * T) * p2 + -T * p3;
        c4 = -T * p0 + (2 - T) * p1 + (T - 2) * p2 + T * p3;

        return ((c4 * u + c3) * u + c2) * u + c1;
    }

    /**
     * Interpolate a spline between at least 4 control points following the Catmull-Rom equation.
     * here is the interpolation matrix
     * m = [ 0.0  1.0  0.0   0.0 ]
     *     [-T    0.0  T     0.0 ]
     *     [ 2T   T-3  3-2T  -T  ]
     *     [-T    2-T  T-2   T   ]
     * where T is the tension of the curve
     * the result is a value between p1 and p2, t=0 for p1, t=1 for p2
     *
     * @param u value from 0 to 1
     * @param T The tension of the curve
     * @param p0 control point 0
     * @param p1 control point 1
     * @param p2 control point 2
     * @param p3 control point 3
     * @param store a Vector3f to store the result
     * @return Catmull–Rom interpolation
     */
    public static Vector3f interpolateCatmullRom(float u, float T, Vector3f p0,
                                                 Vector3f p1, Vector3f p2, Vector3f p3, Vector3f store) {
        if (store == null) {
            store = new Vector3f();
        }
        store.x = interpolateCatmullRom(u, T, p0.x, p1.x, p2.x, p3.x);
        store.y = interpolateCatmullRom(u, T, p0.y, p1.y, p2.y, p3.y);
        store.z = interpolateCatmullRom(u, T, p0.z, p1.z, p2.z, p3.z);
        return store;
    }

    /**
     * Interpolate a spline between at least 4 control points using the
     * Catmull-Rom equation. Here is the interpolation matrix:
     * m = [ 0.0  1.0  0.0   0.0 ]
     *     [-T    0.0  T     0.0 ]
     *     [ 2T   T-3  3-2T  -T  ]
     *     [-T    2-T  T-2   T   ]
     * where T is the tension of the curve
     * the result is a value between p1 and p2, t=0 for p1, t=1 for p2
     *
     * @param u value from 0 to 1
     * @param T The tension of the curve
     * @param p0 control point 0
     * @param p1 control point 1
     * @param p2 control point 2
     * @param p3 control point 3
     * @return Catmull–Rom interpolation
     */
    public static Vector3f interpolateCatmullRom(float u, float T, Vector3f p0,
                                                 Vector3f p1, Vector3f p2, Vector3f p3) {
        return interpolateCatmullRom(u, T, p0, p1, p2, p3, null);
    }

    /**Interpolate a spline between at least 4 control points following the Bezier equation.
     * here is the interpolation matrix
     * m = [ -1.0   3.0  -3.0    1.0 ]
     *     [  3.0  -6.0   3.0    0.0 ]
     *     [ -3.0   3.0   0.0    0.0 ]
     *     [  1.0   0.0   0.0    0.0 ]
     * where T is the curve tension
     * the result is a value between p1 and p3, t=0 for p1, t=1 for p3
     *
     * @param u value from 0 to 1
     * @param p0 control point 0
     * @param p1 control point 1
     * @param p2 control point 2
     * @param p3 control point 3
     * @return Bezier interpolation
     */
    public static float interpolateBezier(float u, float p0, float p1, float p2, float p3) {
        float oneMinusU = 1.0f - u;
        float oneMinusU2 = oneMinusU * oneMinusU;
        float u2 = u * u;
        return p0 * oneMinusU2 * oneMinusU
                + 3.0f * p1 * u * oneMinusU2
                + 3.0f * p2 * u2 * oneMinusU
                + p3 * u2 * u;
    }

    /**
     * Interpolate a spline between at least 4 control points following the Bezier equation.
     * here is the interpolation matrix
     * m = [ -1.0   3.0  -3.0    1.0 ]
     *     [  3.0  -6.0   3.0    0.0 ]
     *     [ -3.0   3.0   0.0    0.0 ]
     *     [  1.0   0.0   0.0    0.0 ]
     * where T is the tension of the curve
     * the result is a value between p1 and p3, t=0 for p1, t=1 for p3
     *
     * @param u value from 0 to 1
     * @param p0 control point 0
     * @param p1 control point 1
     * @param p2 control point 2
     * @param p3 control point 3
     * @param store a Vector3f to store the result
     * @return Bezier interpolation
     */
    public static Vector3f interpolateBezier(float u, Vector3f p0, Vector3f p1,
                                             Vector3f p2, Vector3f p3, Vector3f store) {
        if (store == null) {
            store = new Vector3f();
        }
        store.x = interpolateBezier(u, p0.x, p1.x, p2.x, p3.x);
        store.y = interpolateBezier(u, p0.y, p1.y, p2.y, p3.y);
        store.z = interpolateBezier(u, p0.z, p1.z, p2.z, p3.z);
        return store;
    }

    /**
     * Interpolate a spline between at least 4 control points following the Bezier equation.
     * here is the interpolation matrix
     * m = [ -1.0   3.0  -3.0    1.0 ]
     *     [  3.0  -6.0   3.0    0.0 ]
     *     [ -3.0   3.0   0.0    0.0 ]
     *     [  1.0   0.0   0.0    0.0 ]
     * where T is the tension of the curve
     * the result is a value between p1 and p3, t=0 for p1, t=1 for p3
     *
     * @param u value from 0 to 1
     * @param p0 control point 0
     * @param p1 control point 1
     * @param p2 control point 2
     * @param p3 control point 3
     * @return Bezier interpolation
     */
    public static Vector3f interpolateBezier(float u, Vector3f p0, Vector3f p1, Vector3f p2, Vector3f p3) {
        return interpolateBezier(u, p0, p1, p2, p3, null);
    }

    /**
     * Compute the length of a Catmull–Rom spline between control points 1 and 2
     *
     * @param p0 control point 0
     * @param p1 control point 1
     * @param p2 control point 2
     * @param p3 control point 3
     * @param startRange the starting range on the segment (use 0)
     * @param endRange the end range on the segment (use 1)
     * @param curveTension the curve tension
     * @return the length of the segment
     */
    public static float getCatmullRomP1toP2Length(Vector3f p0, Vector3f p1,
                                                  Vector3f p2, Vector3f p3, float startRange, float endRange, float curveTension) {

        float epsilon = 0.001f;
        float middleValue = (startRange + endRange) * 0.5f;
        Vector3f start = p1.clone();
        if (startRange != 0) {
            interpolateCatmullRom(startRange, curveTension, p0, p1, p2, p3, start);
        }
        Vector3f end = p2.clone();
        if (endRange != 1) {
            interpolateCatmullRom(endRange, curveTension, p0, p1, p2, p3, end);
        }
        Vector3f middle = interpolateCatmullRom(middleValue, curveTension, p0, p1, p2, p3);
        float l = end.subtract(start).length();
        float l1 = middle.subtract(start).length();
        float l2 = end.subtract(middle).length();
        float len = l1 + l2;
        if (l + epsilon < len) {
            l1 = getCatmullRomP1toP2Length(p0, p1, p2, p3, startRange, middleValue, curveTension);
            l2 = getCatmullRomP1toP2Length(p0, p1, p2, p3, middleValue, endRange, curveTension);
        }
        l = l1 + l2;
        return l;
    }

    /**
     * Compute the length on a Bezier spline between control points 1 and 2.
     *
     * @param p0 control point 0
     * @param p1 control point 1
     * @param p2 control point 2
     * @param p3 control point 3
     * @return the length of the segment
     */
    public static float getBezierP1toP2Length(Vector3f p0, Vector3f p1, Vector3f p2, Vector3f p3) {
        float delta = 0.02f, t = 0.0f, result = 0.0f;
        Vector3f v1 = p0.clone(), v2 = new Vector3f();
        while (t <= 1.0f) {
            interpolateBezier(t, p0, p1, p2, p3, v2);
            result += v1.subtractLocal(v2).length();
            v1.set(v2);
            t += delta;
        }
        return result;
    }

    public static float unInterpolateLinear(float value, float min, float max) {
        return (value - min) / (max - min);
    }
}
