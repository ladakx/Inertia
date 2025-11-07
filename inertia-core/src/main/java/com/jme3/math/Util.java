package com.jme3.math;

public class Util {
    public static float[] reverseArray(final float[] arr) {

        final int len = arr.length;
        float temp;

        int i;
        int j;

        for (i = 0, j = len - 1; i < len / 2; ++i, --j) {
            temp = arr[i];
            arr[i] = arr[j];
            arr[j] = temp;
        }

        return arr;
    }
}
