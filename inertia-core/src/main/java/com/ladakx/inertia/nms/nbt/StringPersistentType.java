/*
 * Copyright (c) 2022 Alexander Majka (mfnalex) / JEFF Media GbR
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 *
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 *
 * If you need help or have any suggestions, feel free to join my Discord and head to
 * #programming-help:
 *
 * Discord: https://discord.jeff-media.com/
 *
 * If you find this library helpful or if you're using it one of your paid plugins, please consider
 * leaving a donation to support the further development of this project :)
 *
 * Donations: https://paypal.me/mfnalex
 */

package com.ladakx.inertia.nms.nbt;

import org.bukkit.persistence.PersistentDataAdapterContext;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static com.ladakx.inertia.nms.nbt.StringTagType.getBytes;

/**
 * Persistent data type for storing string arrays.
 */
public class StringPersistentType implements PersistentDataType<byte[], String[]> {

    /**
     * Singleton instance of the persistent data type.
     */
    public static final StringPersistentType INSTANCE = new StringPersistentType();

    /**
     * Private constructor to prevent instantiation.
     */
    private StringPersistentType() {}

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
    public byte @NotNull [] toPrimitive(final String[] complex, @NotNull final PersistentDataAdapterContext context) {
        return getBytes(complex);
    }

    /**
     * Converts a byte array to a string array.
     * @param primitive the primitive value
     * @param context the context this operation is running in
     * @return
     */
    @Override
    public String @NotNull [] fromPrimitive(final byte @NotNull [] primitive, @NotNull final PersistentDataAdapterContext context) {
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