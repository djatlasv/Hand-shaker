package me.mklv.handshaker.fabric.server.utils;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import java.lang.reflect.Method;

/**
 * Adapter for handling permissions with fallback support.
 * Attempts to use fabric-permissions-api if available, otherwise falls back to default behavior.
 * This allows the mod to work whether or not fabric-permissions-api is installed.
 * 
 * For 1.21.11+, supports both CommandSourceStack and ServerPlayer for permission checks.
 */
public class PermissionsAdapter {
    private static final Method PERMISSIONS_CHECK_COMMAND_SOURCE;
    private static final Method PERMISSIONS_CHECK_PLAYER;
    private static final boolean HAS_FABRIC_PERMISSIONS;

    static {
        Method checkCommandSourceMethod = null;
        Method checkPlayerMethod = null;
        boolean hasFabricPermissions = false;
        
        try {
            // Try to load the fabric-permissions-api
            Class<?> permissionsClass = Class.forName("me.lucko.fabric.api.permissions.v0.Permissions");
            
            // Try to get the CommandSourceStack version (takes permission string and int level)
            try {
                checkCommandSourceMethod = permissionsClass.getMethod("check", CommandSourceStack.class, String.class, int.class);
            } catch (NoSuchMethodException e1) {
                // Fallback: try without the int parameter (some versions don't require it)
                try {
                    checkCommandSourceMethod = permissionsClass.getMethod("check", CommandSourceStack.class, String.class);
                } catch (NoSuchMethodException e2) {
                    // Method not found
                }
            }
            
            // Try to get the ServerPlayer version (1.21.11+, takes permission string only)
            try {
                checkPlayerMethod = permissionsClass.getMethod("check", ServerPlayer.class, String.class);
            } catch (NoSuchMethodException e1) {
                // Try with int parameter as fallback
                try {
                    checkPlayerMethod = permissionsClass.getMethod("check", ServerPlayer.class, String.class, int.class);
                } catch (NoSuchMethodException e2) {
                    // Method not found
                }
            }
            
            hasFabricPermissions = checkCommandSourceMethod != null || checkPlayerMethod != null;
        } catch (ClassNotFoundException e) {
            // fabric-permissions-api not available, will use defaults
        }
        
        PERMISSIONS_CHECK_COMMAND_SOURCE = checkCommandSourceMethod;
        PERMISSIONS_CHECK_PLAYER = checkPlayerMethod;
        HAS_FABRIC_PERMISSIONS = hasFabricPermissions;
    }

    /**
     * Check if a CommandSourceStack has a specific permission.
     * If fabric-permissions-api is available, uses it.
     * Otherwise, returns true (no permission checking without the API).
     */
    public static boolean checkPermission(CommandSourceStack source, String permission, int minimumLevel) {
        if (HAS_FABRIC_PERMISSIONS && PERMISSIONS_CHECK_COMMAND_SOURCE != null) {
            try {
                // Try calling with int parameter first
                try {
                    return (boolean) PERMISSIONS_CHECK_COMMAND_SOURCE.invoke(null, source, permission, minimumLevel);
                } catch (IllegalArgumentException e) {
                    // If that fails, try without int parameter
                    return (boolean) PERMISSIONS_CHECK_COMMAND_SOURCE.invoke(null, source, permission);
                }
            } catch (Exception e) {
                // If all reflection fails, allow by default
                return true;
            }
        }
        
        // No fabric-permissions-api, allow by default
        return true;
    }

    /**
     * Check if a ServerPlayer has a specific permission.
     * If fabric-permissions-api is available, uses it.
     * Otherwise, returns true (no permission checking without the API).
     * 
     * @param player The player to check
     * @param permission The permission node to check
     * @return true if the player has the permission, false otherwise
     */
    public static boolean checkPermission(ServerPlayer player, String permission) {
        if (HAS_FABRIC_PERMISSIONS && PERMISSIONS_CHECK_PLAYER != null) {
            try {
                return (boolean) PERMISSIONS_CHECK_PLAYER.invoke(null, player, permission);
            } catch (Exception e) {
                // If reflection fails, allow by default
                return true;
            }
        }
        
        // No fabric-permissions-api, allow by default
        return true;
    }

    /**
     * Check if fabric-permissions-api is available.
     */
    public static boolean hasFabricPermissions() {
        return HAS_FABRIC_PERMISSIONS;
    }
}
