package com.ladakx.inertia.core.physics.threading;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Реалізація подвійного буферування для потокобезпечної передачі
 * результатів симуляції (напр., позицій тіл) з Фізичного потоку
 * до Головного потоку.
 *
 * (Наразі це заглушка, буде розширено в майбутніх модулях)
 */
public final class SimulationResultBuffer {

    // Типізація заглушки: Map<ID, Transform> (буде визначено пізніше)
    private final Map<Object, Object> bufferA = Collections.emptyMap();
    private final Map<Object, Object> bufferB = Collections.emptyMap();

    // AtomicReference для атомарної заміни буферів
    private final AtomicReference<Map<Object, Object>> readBuffer;
    private Map<Object, Object> writeBuffer;

    public SimulationResultBuffer() {
        this.writeBuffer = bufferA;
        this.readBuffer = new AtomicReference<>(bufferB);
    }

    /**
     * Отримує буфер, безпечний для читання з Головного потоку.
     *
     * @return Map<?, ?> з результатами симуляції.
     */
    public Map<Object, Object> getReadBuffer() {
        return readBuffer.get();
    }

    /**
     * Атомарно міняє місцями буфери читання та запису.
     * (Викликається з Фізичного потоку)
     */
    public void swapBuffers() {
        // writeBuffer (A) стає новим readBuffer
        // Старий readBuffer (B) стає новим writeBuffer
        Map<Object, Object> oldReadBuffer = this.readBuffer.getAndSet(this.writeBuffer);
        this.writeBuffer = oldReadBuffer;
    }

    /**
     * Отримує буфер для запису.
     * (Викликається з Фізичного потоку)
     *
     * @return Map<?, ?> куди можна записувати нові результати.
     */
    public Map<Object, Object> getWriteBuffer() {
        // TODO: Очистити або переконатися, що буфер готовий до запису
        // writeBuffer.clear();
        return writeBuffer;
    }
}