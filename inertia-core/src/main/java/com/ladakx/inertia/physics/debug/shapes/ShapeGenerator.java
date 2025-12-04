package com.ladakx.inertia.physics.debug.shapes;

import org.bukkit.Location;
import org.bukkit.util.Vector;

import java.util.List;

/**
 * Інтерфейс стратегії для генерації форм з блоків.
 */
public interface ShapeGenerator {

    /**
     * Генерує список локацій для спавну блоків у заданій формі.
     */
    List<Vector> generatePoints(Location center, double... params);

    /**
     * Повертає назву форми.
     */
    String getName();

    /**
     * Повертає опис аргументів (наприклад, "<radius>").
     */
    String getUsage();

    /**
     * Повертає кількість очікуваних параметрів (чисел) перед ID тіла.
     */
    int getParamCount();
}