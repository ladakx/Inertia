package com.ladakx.inertia.common.serializers;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Serialize Inventory to string and back using Base64 encoding
 */
public class InventorySerializer {

	private InventorySerializer () {
		// utility class
	}

    /**
	 * A method to serialize an inventory to Base64 string.
	 *
	 * @param inventory to serialize
	 * @return Base64 string of the provided inventory
	 */
	public static String toBase64(Inventory inventory) {
		if (inventory == null) return "0";
		
	    try {
	        ByteArrayOutputStream baos = new ByteArrayOutputStream();
	        BukkitObjectOutputStream oos = new BukkitObjectOutputStream(baos);
	
	        oos.writeInt(inventory.getSize());
	
	        for (int i = 0; i < inventory.getSize(); i++) {
	        	oos.writeObject(inventory.getItem(i));
	        }
	
	        oos.close();
	        return Base64Coder.encodeLines(baos.toByteArray());
	    } catch (Exception e) {
	        throw new IllegalStateException("Unable to save trunk in world. ", e);
	    }
	}

	/**
     * A method to get an {@link Inventory} from an encoded, Base64, string.
     *
     * @param data Base64 string of data containing an inventory.
     * @return Inventory created from the Base64 string.
     */
    public static Inventory fromBase64(String data, InventoryHolder holder, Component title, String error) {
    	if (data == null) return null;
    	if (data.equals("0") || data.isEmpty()) return null;

        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64Coder.decodeLines(data));
            BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);
            Inventory inventory = Bukkit.getServer().createInventory(holder, dataInput.readInt(), LegacyComponentSerializer.legacyAmpersand().serialize(title));

            for (int i = 0; i < inventory.getSize(); i++) {
                inventory.setItem(i, (ItemStack) dataInput.readObject());
            }

            dataInput.close();
            return inventory;
        } catch (ClassNotFoundException | IOException e) {
            throw new IllegalStateException(error, e);
        }
    }
}
