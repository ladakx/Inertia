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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class DynamicBodyStorageFile {

    private static final int VERSION = 3;
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
                    out.writeLong(record.clusterId().getMostSignificantBits());
                    out.writeLong(record.clusterId().getLeastSignificantBits());
                    out.writeUTF(record.world());
                    out.writeUTF(record.bodyId());
                    out.writeByte(record.motionType().ordinal());
                    out.writeFloat(record.friction());
                    out.writeFloat(record.restitution());
                    out.writeFloat(record.gravityFactor());
                    out.writeInt(record.chunkX());
                    out.writeInt(record.chunkZ());
                    out.writeLong(record.savedAtEpochMillis());

                    out.writeInt(record.customData().size());
                    for (Map.Entry<String, String> e : record.customData().entrySet()) {
                        out.writeUTF(e.getKey());
                        out.writeUTF(e.getValue());
                    }

                    out.writeInt(record.parts().size());
                    for (PartState ps : record.parts()) {
                        out.writeUTF(ps.partKey());
                        out.writeDouble(ps.x());
                        out.writeDouble(ps.y());
                        out.writeDouble(ps.z());
                        out.writeFloat(ps.rX());
                        out.writeFloat(ps.rY());
                        out.writeFloat(ps.rZ());
                        out.writeFloat(ps.rW());
                        out.writeDouble(ps.lvX());
                        out.writeDouble(ps.lvY());
                        out.writeDouble(ps.lvZ());
                        out.writeDouble(ps.avX());
                        out.writeDouble(ps.avY());
                        out.writeDouble(ps.avZ());
                        out.writeBoolean(ps.anchored());
                        out.writeDouble(ps.anchorX());
                        out.writeDouble(ps.anchorY());
                        out.writeDouble(ps.anchorZ());
                    }
                }
            }
            Files.move(tempFile, storageFile, StandardCopyOption.REPLACE_EXISTING);
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
                InertiaLogger.warn("Terrain storage version mismatch or invalid magic. Found V" + version + ". Clearing storage to prevent errors.");
                return List.of(); // Safely return empty, preventing V2 load attempts with V3 structures
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

                    byte motionTypeOrdinal = in.readByte();
                    MotionType motionType = MotionType.values()[Math.max(0, Math.min(MotionType.values().length - 1, motionTypeOrdinal))];
                    float friction = in.readFloat();
                    float restitution = in.readFloat();
                    float gravityFactor = in.readFloat();

                    int chunkX = in.readInt();
                    int chunkZ = in.readInt();
                    long savedAt = in.readLong();

                    int cDataSize = in.readInt();
                    Map<String, String> customData = new HashMap<>(cDataSize);
                    for (int j = 0; j < cDataSize; j++) {
                        customData.put(in.readUTF(), in.readUTF());
                    }

                    int partsSize = in.readInt();
                    List<PartState> parts = new ArrayList<>(partsSize);
                    for (int j = 0; j < partsSize; j++) {
                        parts.add(new PartState(
                                in.readUTF(),
                                in.readDouble(), in.readDouble(), in.readDouble(),
                                in.readFloat(), in.readFloat(), in.readFloat(), in.readFloat(),
                                in.readDouble(), in.readDouble(), in.readDouble(),
                                in.readDouble(), in.readDouble(), in.readDouble(),
                                in.readBoolean(),
                                in.readDouble(), in.readDouble(), in.readDouble()
                        ));
                    }

                    result.add(new DynamicBodyStorageRecord(
                            id, world, bodyId, motionType, friction, restitution, gravityFactor,
                            chunkX, chunkZ, savedAt, customData, parts
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

    public void clear() {
        try {
            Files.deleteIfExists(storageFile);
        } catch (IOException e) {
            InertiaLogger.warn("Failed to delete dynamic body storage file", e);
        }
    }
}