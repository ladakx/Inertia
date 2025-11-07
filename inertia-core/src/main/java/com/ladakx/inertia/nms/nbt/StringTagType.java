package com.ladakx.inertia.nms.nbt;

import org.bukkit.inventory.meta.tags.ItemTagAdapterContext;
import org.bukkit.inventory.meta.tags.ItemTagType;
import org.jetbrains.annotations.NotNull;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Tag type for storing string arrays.
 */
public class StringTagType implements ItemTagType<byte[], String[]> {

    /**
     * Singleton instance of the tag type.
     */
    public static final StringTagType INSTANCE = new StringTagType();

    /**
     * Private constructor to prevent instantiation.
     */
    private StringTagType() {}

    /**
     * Returns the primitive type.
     * @return the primitive type
     */
    @NotNull @Override
    public Class<byte[]> getPrimitiveType() {
        return byte[].class;
    }

    /**
     * Returns the complex type.
     * @return the complex type
     */
    @NotNull @Override
    public Class<String[]> getComplexType() {
        return String[].class;
    }

    /**
     * Converts a string array to a byte array.
     * @param complex the complex object instance
     * @param context the context this operation is running in
     * @return the primitive value
     */
    @Override
    public byte @NotNull [] toPrimitive(String[] complex, @NotNull ItemTagAdapterContext context) {
        return getBytes(complex);
    }

    /**
     * Converts a byte array to a string array.
     * @param complex the primitive value
     * @return the complex object instance
     */
    static byte @NotNull [] getBytes(String[] complex) {
        final byte[][] allBytes = new byte[complex.length][];
        int total = 0;
        for (int i = 0; i < allBytes.length; i++) {
            final byte[] bytes = complex[i].getBytes(StandardCharsets.UTF_8);
            allBytes[i] = bytes;
            total += bytes.length;
        }

        final ByteBuffer buffer = ByteBuffer.allocate(total + allBytes.length * 4); // stores integers
        for (final byte[] bytes : allBytes) {
            buffer.putInt(bytes.length);
            buffer.put(bytes);
        }

        return buffer.array();
    }

    /**
     * Converts a byte array to a string array.
     * @param primitive the primitive value
     * @param context the context this operation is running in
     * @return the complex object instance
     */
    @Override
    public String @NotNull [] fromPrimitive(byte @NotNull [] primitive, @NotNull ItemTagAdapterContext context) {
        final ByteBuffer buffer = ByteBuffer.wrap(primitive);
        final List<String> list = new ArrayList<>();

        while (buffer.remaining() > 0) {
            if (buffer.remaining() < 4)
                break;
            final int stringLength = buffer.getInt();
            if (buffer.remaining() < stringLength)
                break;

            final byte[] stringBytes = new byte[stringLength];
            buffer.get(stringBytes);

            list.add(new String(stringBytes, StandardCharsets.UTF_8));
        }

        return list.toArray(new String[0]);
    }
}