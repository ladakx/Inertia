package com.ladakx.inertia.common.metrics;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class RollingAverage {
    private final int size;
    private final double[] samples;
    private int index = 0;
    private double sum = 0;
    private boolean filled = false;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public RollingAverage(int size) {
        if (size <= 0) throw new IllegalArgumentException("Size must be greater than 0");
        this.size = size;
        this.samples = new double[size];
    }

    public void add(double value) {
        lock.writeLock().lock();
        try {
            sum -= samples[index];
            samples[index] = value;
            sum += value;
            index++;
            if (index == size) {
                index = 0;
                filled = true;
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public double getAverage() {
        lock.readLock().lock();
        try {
            int count = filled ? size : index;
            if (count == 0) return 0;
            return sum / count;
        } finally {
            lock.readLock().unlock();
        }
    }

    public double getMax() {
        lock.readLock().lock();
        try {
            int count = filled ? size : index;
            if (count == 0) return 0;
            double max = Double.MIN_VALUE;
            for (int i = 0; i < count; i++) {
                if (samples[i] > max) max = samples[i];
            }
            return max;
        } finally {
            lock.readLock().unlock();
        }
    }

    public double getAverageRounded(int scale) {
        return BigDecimal.valueOf(getAverage())
                .setScale(scale, RoundingMode.HALF_UP)
                .doubleValue();
    }
}