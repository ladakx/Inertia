package com.ladakx.inertia.api.body.type;

import com.ladakx.inertia.physics.body.InertiaPhysicsBody;

/**
 * Представляет собой отдельное звено цепи.
 */
public interface IChain extends InertiaPhysicsBody {

    /**
     * Возвращает индекс этого звена в цепи (0 - начало).
     */
    int getLinkIndex();

    /**
     * Возвращает общую длину цепи (количество звеньев), частью которой является это тело.
     */
    int getChainLength();

    /**
     * Разрывает соединение с предыдущим звеном (родительским телом).
     * Если это первое звено, действие может не иметь эффекта или отцепить его от точки крепления.
     */
    void breakLink();
}