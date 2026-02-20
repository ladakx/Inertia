package com.ladakx.inertia.physics.persistence.storage;

import com.ladakx.inertia.common.logging.InertiaLogger;
import com.ladakx.inertia.api.body.MotionType;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class DynamicBodyStorageFile {

    private static final int VERSION = 2;
    private static final int MAGIC = 0x494E4459;

    private final Path storageFile;

    public DynamicBodyStorageFile(Path storageFile) {
        this.storageFile = Objects.requireNonNull(storageFile, "storageFile");
    }

    public void write(List<DynamicBodyStorageRecord> records) {
        Objects.requireNonNull(records, "records");
        try {
            Files.createDirectories(storageFile.getParent());
            Path tempFile = storageFile.resolveSibling(storageFile.getFileName().toString() + ".tmp");
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(tempFile)))) {
                out.writeInt(MAGIC);
                out.writeInt(VERSION);
                out.writeInt(records.size());
                for (DynamicBodyStorageRecord record : records) {
                    out.writeLong(record.objectId().getMostSignificantBits());
                    out.writeLong(record.objectId().getLeastSignificantBits());
                    out.writeUTF(record.world());
                    out.writeUTF(record.bodyId());
                    out.writeDouble(record.x());
                    out.writeDouble(record.y());
                    out.writeDouble(record.z());
                    out.writeFloat(record.rotationX());
                    out.writeFloat(record.rotationY());
                    out.writeFloat(record.rotationZ());
                    out.writeFloat(record.rotationW());
                    out.writeDouble(record.linearVelocityX());
                    out.writeDouble(record.linearVelocityY());
                    out.writeDouble(record.linearVelocityZ());
                    out.writeDouble(record.angularVelocityX());
                    out.writeDouble(record.angularVelocityY());
                    out.writeDouble(record.angularVelocityZ());
                    out.writeFloat(record.friction());
                    out.writeFloat(record.restitution());
                    out.writeFloat(record.gravityFactor());
                    out.writeByte(record.motionType().ordinal());
                    out.writeInt(record.chunkX());
                    out.writeInt(record.chunkZ());
                    out.writeLong(record.savedAtEpochMillis());
                }
            }
            Files.move(tempFile, storageFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            InertiaLogger.error("Dynamic body persistence write failed", e);
        }
    }

    public List<DynamicBodyStorageRecord> read() {
        if (!Files.exists(storageFile)) {
            return List.of();
        }
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(storageFile)))) {
            int magic = in.readInt();
            int version = in.readInt();
            if (magic != MAGIC || version != VERSION) {
                return List.of();
            }
            int size = in.readInt();
            if (size <= 0) {
                return List.of();
            }
            List<DynamicBodyStorageRecord> result = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                try {
                    UUID id = new UUID(in.readLong(), in.readLong());
                    String world = in.readUTF();
                    String bodyId = in.readUTF();
                    double x = in.readDouble();
                    double y = in.readDouble();
                    double z = in.readDouble();
                    float rotationX = in.readFloat();
                    float rotationY = in.readFloat();
                    float rotationZ = in.readFloat();
                    float rotationW = in.readFloat();
                    double linearVelocityX = in.readDouble();
                    double linearVelocityY = in.readDouble();
                    double linearVelocityZ = in.readDouble();
                    double angularVelocityX = in.readDouble();
                    double angularVelocityY = in.readDouble();
                    double angularVelocityZ = in.readDouble();
                    float friction = in.readFloat();
                    float restitution = in.readFloat();
                    float gravityFactor = in.readFloat();
                    byte motionTypeOrdinal = in.readByte();
                    MotionType motionType = MotionType.values()[Math.max(0, Math.min(MotionType.values().length - 1, motionTypeOrdinal))];
                    int chunkX = in.readInt();
                    int chunkZ = in.readInt();
                    long savedAt = in.readLong();
                    result.add(new DynamicBodyStorageRecord(
                            id,
                            world,
                            bodyId,
                            x,
                            y,
                            z,
                            rotationX,
                            rotationY,
                            rotationZ,
                            rotationW,
                            linearVelocityX,
                            linearVelocityY,
                            linearVelocityZ,
                            angularVelocityX,
                            angularVelocityY,
                            angularVelocityZ,
                            friction,
                            restitution,
                            gravityFactor,
                            motionType,
                            chunkX,
                            chunkZ,
                            savedAt
                    ));
                } catch (EOFException eofException) {
                    InertiaLogger.warn("Dynamic body persistence file is partially truncated");
                    break;
                }
            }
            return result;
        } catch (IOException e) {
            InertiaLogger.error("Dynamic body persistence read failed", e);
            return List.of();
        }
    }
}
