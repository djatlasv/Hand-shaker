package me.mklv.handshaker.fabric.server;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import me.mklv.handshaker.fabric.server.configs.ConfigManager;
import me.mklv.handshaker.fabric.server.utils.PermissionsAdapter;
import me.mklv.handshaker.fabric.server.utils.PlayerHistoryDatabase;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import java.util.*;
import java.util.concurrent.CompletableFuture;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class HandShakerCommand {
    
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var handshaker = literal("handshaker")
            .requires(source -> PermissionsAdapter.checkPermission(source, "handshaker.admin", 4))
            .executes(HandShakerCommand::showHelp)
            // Core Commands
            .then(literal("reload")
                .executes(HandShakerCommand::reload))
            .then(literal("info")
                .executes(HandShakerCommand::showInfo)
                .then(literal("configured_mods")
                    .executes(HandShakerCommand::showConfiguredMods))
                .then(literal("all_mods")
                    .executes(HandShakerCommand::showAllMods)
                    .then(argument("page", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1))
                        .executes(HandShakerCommand::showAllModsWithPage)))
                .then(literal("mod")
                    .then(argument("modName", StringArgumentType.word())
                        .suggests(HandShakerCommand::suggestAllMods)
                        .executes(HandShakerCommand::showModInfo))))
            .then(literal("config")
                .executes(HandShakerCommand::showConfig)
                .then(literal("behavior")
                    .then(argument("value", StringArgumentType.word())
                        .suggests((ctx, builder) -> builder.suggest("STRICT").suggest("VANILLA").buildFuture())
                        .executes(ctx -> setConfigValue(ctx, "behavior"))))
                .then(literal("integrity")
                    .then(argument("value", StringArgumentType.word())
                        .suggests((ctx, builder) -> builder.suggest("SIGNED").suggest("DEV").buildFuture())
                        .executes(ctx -> setConfigValue(ctx, "integrity"))))
                .then(literal("whitelist")
                    .then(argument("value", StringArgumentType.word())
                        .suggests((ctx, builder) -> builder.suggest("true").suggest("false").buildFuture())
                        .executes(ctx -> setConfigValue(ctx, "whitelist"))))
                .then(literal("bedrock")
                    .then(argument("value", StringArgumentType.word())
                        .suggests((ctx, builder) -> builder.suggest("true").suggest("false").buildFuture())
                        .executes(ctx -> setConfigValue(ctx, "bedrock"))))
                .then(literal("playerdb")
                    .then(argument("value", StringArgumentType.word())
                        .suggests((ctx, builder) -> builder.suggest("true").suggest("false").buildFuture())
                        .executes(ctx -> setConfigValue(ctx, "playerdb")))))
            .then(literal("mode")
                .then(argument("list", StringArgumentType.word())
                    .suggests(HandShakerCommand::suggestModeLists)
                    .then(argument("action", StringArgumentType.word())
                        .suggests(HandShakerCommand::suggestCurrentModeState)
                        .executes(HandShakerCommand::setMode))))
            // Manage subcommands
            .then(literal("manage")
                .then(literal("add")
                    .then(literal("*")
                        .then(argument("mode", StringArgumentType.word())
                            .suggests(HandShakerCommand::suggestModes)
                            .executes(HandShakerCommand::addMod)
                            .then(argument("action", StringArgumentType.word())
                                .suggests(HandShakerCommand::suggestActions)
                                .executes(HandShakerCommand::addModWithAction))))
                    .then(argument("mod", StringArgumentType.word())
                        .suggests(HandShakerCommand::suggestMods)
                        .then(argument("mode", StringArgumentType.word())
                            .suggests(HandShakerCommand::suggestModes)
                            .executes(HandShakerCommand::addMod)
                            .then(argument("action", StringArgumentType.word())
                                .suggests(HandShakerCommand::suggestActions)
                                .executes(HandShakerCommand::addModWithAction)))))
                .then(literal("change")
                    .then(argument("mod", StringArgumentType.word())
                        .suggests(HandShakerCommand::suggestConfiguredMods)
                        .then(argument("mode", StringArgumentType.word())
                            .suggests(HandShakerCommand::suggestModes)
                            .executes(HandShakerCommand::changeMod)
                            .then(argument("action", StringArgumentType.word())
                                .suggests(HandShakerCommand::suggestActions)
                                .executes(HandShakerCommand::changeModWithAction)))))
                .then(literal("remove")
                    .then(argument("mod", StringArgumentType.word())
                        .suggests(HandShakerCommand::suggestConfiguredMods)
                        .executes(HandShakerCommand::removeMod)))
                .then(literal("ignore")
                    .then(literal("add")
                        .then(literal("*")
                            .executes(HandShakerCommand::addIgnore))
                        .then(argument("mod", StringArgumentType.word())
                            .suggests(HandShakerCommand::suggestMods)
                            .executes(HandShakerCommand::addIgnore)))
                    .then(literal("remove")
                        .then(argument("mod", StringArgumentType.word())
                            .suggests(HandShakerCommand::suggestIgnoredMods)
                            .executes(HandShakerCommand::removeIgnore)))
                    .then(literal("list")
                        .executes(HandShakerCommand::listIgnore)))
                .then(literal("player")
                    .then(argument("player", StringArgumentType.word())
                        .suggests(HandShakerCommand::suggestPlayers)
                        .executes(HandShakerCommand::showPlayerMods)
                        .then(literal("*")
                            .then(argument("mode", StringArgumentType.word())
                                .suggests(HandShakerCommand::suggestModes)
                                .executes(HandShakerCommand::setPlayerModStatus)))
                        .then(argument("mod", StringArgumentType.word())
                            .suggests(HandShakerCommand::suggestPlayerMods)
                            .then(argument("mode", StringArgumentType.word())
                                .suggests(HandShakerCommand::suggestModes)
                                .executes(HandShakerCommand::setPlayerModStatus))))));
        
        dispatcher.register(handshaker);
    }

    private static int showHelp(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSystemMessage(Component.literal("═══════════════════════════════").withStyle(ChatFormatting.GOLD));
        ctx.getSource().sendSystemMessage(Component.literal("HandShaker v6 Commands").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        ctx.getSource().sendSystemMessage(Component.literal("═══════════════════════════════").withStyle(ChatFormatting.GOLD));
        
        ctx.getSource().sendSystemMessage(Component.literal("Core Commands:").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
        ctx.getSource().sendSystemMessage(Component.literal("/handshaker reload").withStyle(ChatFormatting.YELLOW)
            .append(Component.literal(" - Reload config").withStyle(ChatFormatting.GRAY)));
        ctx.getSource().sendSystemMessage(Component.literal("/handshaker info [mod]").withStyle(ChatFormatting.YELLOW)
            .append(Component.literal(" - Show statistics").withStyle(ChatFormatting.GRAY)));
        ctx.getSource().sendSystemMessage(Component.literal("/handshaker config [param] [value]").withStyle(ChatFormatting.YELLOW)
            .append(Component.literal(" - View/change configuration").withStyle(ChatFormatting.GRAY)));
        ctx.getSource().sendSystemMessage(Component.literal("/handshaker mode <mods_required|mods_blacklisted|mods_whitelisted> <on|off>").withStyle(ChatFormatting.YELLOW)
            .append(Component.literal(" - Toggle mod lists").withStyle(ChatFormatting.GRAY)));
        
        ctx.getSource().sendSystemMessage(Component.empty());
        ctx.getSource().sendSystemMessage(Component.literal("Mod Management:").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
        ctx.getSource().sendSystemMessage(Component.literal("/handshaker manage add <mod> <mode> [action]").withStyle(ChatFormatting.YELLOW)
            .append(Component.literal(" - Add mod").withStyle(ChatFormatting.GRAY)));
        ctx.getSource().sendSystemMessage(Component.literal("/handshaker manage change <mod> <mode> [action]").withStyle(ChatFormatting.YELLOW)
            .append(Component.literal(" - Change mod").withStyle(ChatFormatting.GRAY)));
        ctx.getSource().sendSystemMessage(Component.literal("/handshaker manage remove <mod>").withStyle(ChatFormatting.YELLOW)
            .append(Component.literal(" - Remove mod").withStyle(ChatFormatting.GRAY)));
        
        return Command.SINGLE_SUCCESS;
    }

    private static int reload(CommandContext<CommandSourceStack> ctx) {
        ConfigManager config = HandShakerServer.getInstance().getConfigManager();
        config.load();
        Component message = Component.literal("✓ HandShaker config reloaded").withStyle(ChatFormatting.GREEN);
        ctx.getSource().sendSuccess(() -> message, true);
        HandShakerServer.getInstance().checkAllPlayers();
        return Command.SINGLE_SUCCESS;
    }

    private static int showConfig(CommandContext<CommandSourceStack> ctx) {
        ConfigManager config = HandShakerServer.getInstance().getConfigManager();
        ctx.getSource().sendSystemMessage(Component.literal("═══════════════════════════════").withStyle(ChatFormatting.GOLD));
        ctx.getSource().sendSystemMessage(Component.literal("HandShaker Configuration").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        ctx.getSource().sendSystemMessage(Component.literal("═══════════════════════════════").withStyle(ChatFormatting.GOLD));
        
        ctx.getSource().sendSystemMessage(Component.literal("Behavior: ").withStyle(ChatFormatting.YELLOW)
            .append(Component.literal(config.getBehavior().toString()).withStyle(ChatFormatting.WHITE)));
        ctx.getSource().sendSystemMessage(Component.literal("Integrity Mode: ").withStyle(ChatFormatting.YELLOW)
            .append(Component.literal(config.getIntegrityMode().toString()).withStyle(ChatFormatting.WHITE)));
        ctx.getSource().sendSystemMessage(Component.literal("Whitelist Mode: ").withStyle(ChatFormatting.YELLOW)
            .append(Component.literal(config.isWhitelist() ? "ON" : "OFF").withStyle(ChatFormatting.WHITE)));
        ctx.getSource().sendSystemMessage(Component.literal("Bedrock Players: ").withStyle(ChatFormatting.YELLOW)
            .append(Component.literal(config.isAllowBedrockPlayers() ? "Allowed" : "Blocked").withStyle(ChatFormatting.WHITE)));
        ctx.getSource().sendSystemMessage(Component.literal("Player Database: ").withStyle(ChatFormatting.YELLOW)
            .append(Component.literal(config.isPlayerdbEnabled() ? "Enabled" : "Disabled").withStyle(ChatFormatting.WHITE)));
        
        ctx.getSource().sendSystemMessage(Component.literal("Required Mods Enabled: ").withStyle(ChatFormatting.YELLOW)
            .append(Component.literal(config.areModsRequiredEnabled() ? "ON" : "OFF").withStyle(ChatFormatting.WHITE)));
        ctx.getSource().sendSystemMessage(Component.literal("Blacklisted Mods Enabled: ").withStyle(ChatFormatting.YELLOW)
            .append(Component.literal(config.areModsBlacklistedEnabled() ? "ON" : "OFF").withStyle(ChatFormatting.WHITE)));
        ctx.getSource().sendSystemMessage(Component.literal("Whitelisted Mods Enabled: ").withStyle(ChatFormatting.YELLOW)
            .append(Component.literal(config.areModsWhitelistedEnabled() ? "ON" : "OFF").withStyle(ChatFormatting.WHITE)));
        
        return Command.SINGLE_SUCCESS;
    }

    private static int setConfigValue(CommandContext<CommandSourceStack> ctx, String param) {
        String value = StringArgumentType.getString(ctx, "value");
        ConfigManager config = HandShakerServer.getInstance().getConfigManager();
        
        switch (param) {
            case "behavior" -> {
                if (!value.equalsIgnoreCase("STRICT") && !value.equalsIgnoreCase("VANILLA")) {
                    ctx.getSource().sendFailure(Component.literal("Behavior must be STRICT or VANILLA"));
                    return 0;
                }
                config.setBehavior(value);
                ctx.getSource().sendSuccess(() -> Component.literal("✓ Behavior set to " + value.toUpperCase()).withStyle(ChatFormatting.GREEN), true);
                HandShakerServer.getInstance().checkAllPlayers();
            }
            case "integrity" -> {
                if (!value.equalsIgnoreCase("SIGNED") && !value.equalsIgnoreCase("DEV")) {
                    ctx.getSource().sendFailure(Component.literal("Integrity must be SIGNED or DEV"));
                    return 0;
                }
                config.setIntegrityMode(value);
                ctx.getSource().sendSuccess(() -> Component.literal("✓ Integrity mode set to " + value.toUpperCase()).withStyle(ChatFormatting.GREEN), true);
            }
            case "whitelist" -> {
                if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
                    ctx.getSource().sendFailure(Component.literal("Whitelist must be true or false"));
                    return 0;
                }
                boolean enable = value.equalsIgnoreCase("true");
                config.setWhitelist(enable);
                ctx.getSource().sendSuccess(() -> Component.literal("✓ Whitelist mode " + (enable ? "ON" : "OFF")).withStyle(ChatFormatting.GREEN), true);
                HandShakerServer.getInstance().checkAllPlayers();
            }
            case "bedrock" -> {
                if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
                    ctx.getSource().sendFailure(Component.literal("Bedrock must be true or false"));
                    return 0;
                }
                boolean allow = value.equalsIgnoreCase("true");
                config.setAllowBedrockPlayers(allow);
                ctx.getSource().sendSuccess(() -> Component.literal("✓ Bedrock players " + (allow ? "allowed" : "blocked")).withStyle(ChatFormatting.GREEN), true);
                HandShakerServer.getInstance().checkAllPlayers();
            }
            case "playerdb" -> {
                if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
                    ctx.getSource().sendFailure(Component.literal("Player database must be true or false"));
                    return 0;
                }
                boolean enable = value.equalsIgnoreCase("true");
                config.setPlayerdbEnabled(enable);
                ctx.getSource().sendSuccess(() -> Component.literal("✓ Player database " + (enable ? "enabled" : "disabled")).withStyle(ChatFormatting.GREEN), true);
            }
            default -> {
                ctx.getSource().sendFailure(Component.literal("Unknown config parameter: " + param));
                return 0;
            }
        }
        
        config.save();
        return Command.SINGLE_SUCCESS;
    }

    private static int addMod(CommandContext<CommandSourceStack> ctx) {
        // Try to get mod from argument, fallback to "*" if it's a literal
        String modId;
        try {
            modId = StringArgumentType.getString(ctx, "mod").trim().toLowerCase();
        } catch (IllegalArgumentException e) {
            // mod was a literal("*"), so just use "*"
            modId = "*";
        }
        
        String mode = StringArgumentType.getString(ctx, "mode").toLowerCase();
        ConfigManager config = HandShakerServer.getInstance().getConfigManager();
        
        if (!isValidMode(mode)) {
            ctx.getSource().sendFailure(Component.literal("Invalid mode. Use: required, blacklisted, or allowed"));
            return 0;
        }
        
        if (modId.equals("*")) {
            ServerPlayer player = ctx.getSource().getEntity() instanceof ServerPlayer ? (ServerPlayer) ctx.getSource().getEntity() : null;
            if (player == null) {
                ctx.getSource().sendFailure(Component.literal("Only players can use * wildcard"));
                return 0;
            }
            
            HandShakerServer.ClientInfo info = HandShakerServer.getInstance().getClients().get(player.getUUID());
            if (info == null || info.mods().isEmpty()) {
                ctx.getSource().sendFailure(Component.literal("No mods found on your client"));
                return 0;
            }
            
            int added = 0;
            for (String mod : info.mods()) {
                if (!config.isIgnored(mod)) {
                    config.setModConfig(mod, mode, "kick", null);
                    added++;
                }
            }
            final int finalAdded = added;
            final String finalMode = mode;
            ctx.getSource().sendSuccess(() -> Component.literal("✓ Added " + finalAdded + " of your mods as " + finalMode).withStyle(ChatFormatting.GREEN), true);
        } else {
            final String finalModId = modId;
            final String finalMode = mode;
            config.setModConfig(modId, mode, "kick", null);
            ctx.getSource().sendSuccess(() -> Component.literal("✓ Added " + finalModId + " as " + finalMode).withStyle(ChatFormatting.GREEN), true);
        }
        
        HandShakerServer.getInstance().checkAllPlayers();
        return Command.SINGLE_SUCCESS;
    }

    private static int addModWithAction(CommandContext<CommandSourceStack> ctx) {
        // Try to get mod from argument, fallback to "*" if it's a literal
        String modId;
        try {
            modId = StringArgumentType.getString(ctx, "mod").trim().toLowerCase();
        } catch (IllegalArgumentException e) {
            // mod was a literal("*"), so just use "*"
            modId = "*";
        }
        
        String mode = StringArgumentType.getString(ctx, "mode").toLowerCase();
        String action = StringArgumentType.getString(ctx, "action").toLowerCase();
        ConfigManager config = HandShakerServer.getInstance().getConfigManager();
        
        if (!isValidMode(mode)) {
            ctx.getSource().sendFailure(Component.literal("Invalid mode. Use: required, blacklisted, or allowed"));
            return 0;
        }
        
        if (!isValidAction(action)) {
            Set<String> availableActions = config.getAvailableActions();
            String actionList = availableActions.isEmpty() ? "kick, ban" : String.join(", ", availableActions);
            ctx.getSource().sendFailure(Component.literal("Invalid action. Available: " + actionList));
            return 0;
        }
        
        if (modId.equals("*")) {
            ServerPlayer player = ctx.getSource().getEntity() instanceof ServerPlayer ? (ServerPlayer) ctx.getSource().getEntity() : null;
            if (player == null) {
                ctx.getSource().sendFailure(Component.literal("Only players can use * wildcard"));
                return 0;
            }
            
            HandShakerServer.ClientInfo info = HandShakerServer.getInstance().getClients().get(player.getUUID());
            if (info == null || info.mods().isEmpty()) {
                ctx.getSource().sendFailure(Component.literal("No mods found on your client"));
                return 0;
            }
            
            int added = 0;
            for (String mod : info.mods()) {
                if (!config.isIgnored(mod)) {
                    config.setModConfig(mod, mode, action, null);
                    added++;
                }
            }
            final int finalAdded = added;
            final String finalMode = mode;
            final String finalAction = action;
            ctx.getSource().sendSuccess(() -> Component.literal("✓ Added " + finalAdded + " of your mods as " + finalMode + " with " + finalAction).withStyle(ChatFormatting.GREEN), true);
        } else {
            final String finalModId = modId;
            final String finalMode = mode;
            final String finalAction = action;
            config.setModConfig(modId, mode, action, null);
            ctx.getSource().sendSuccess(() -> Component.literal("✓ Added " + finalModId + " as " + finalMode + " with " + finalAction).withStyle(ChatFormatting.GREEN), true);
        }
        
        HandShakerServer.getInstance().checkAllPlayers();
        return Command.SINGLE_SUCCESS;
    }

    private static int changeMod(CommandContext<CommandSourceStack> ctx) {
        String modId = StringArgumentType.getString(ctx, "mod").toLowerCase();
        String mode = StringArgumentType.getString(ctx, "mode").toLowerCase();
        ConfigManager config = HandShakerServer.getInstance().getConfigManager();
        
        if (!isValidMode(mode)) {
            ctx.getSource().sendFailure(Component.literal("Invalid mode. Use: required, blacklisted, or allowed"));
            return 0;
        }
        
        ConfigManager.ModConfig oldConfig = config.getModConfig(modId);
        config.setModConfig(modId, mode, oldConfig.getAction().toString().toLowerCase(), oldConfig.getWarnMessage());
        ctx.getSource().sendSuccess(() -> Component.literal("✓ Changed " + modId + " to " + mode).withStyle(ChatFormatting.GREEN), true);
        HandShakerServer.getInstance().checkAllPlayers();
        return Command.SINGLE_SUCCESS;
    }

    private static int changeModWithAction(CommandContext<CommandSourceStack> ctx) {
        String modId = StringArgumentType.getString(ctx, "mod").toLowerCase();
        String mode = StringArgumentType.getString(ctx, "mode").toLowerCase();
        String action = StringArgumentType.getString(ctx, "action").toLowerCase();
        ConfigManager config = HandShakerServer.getInstance().getConfigManager();
        
        if (!isValidMode(mode)) {
            ctx.getSource().sendFailure(Component.literal("Invalid mode. Use: required, blacklisted, or allowed"));
            return 0;
        }
        
        if (!isValidAction(action)) {
            ctx.getSource().sendFailure(Component.literal("Invalid action. Use: kick or ban"));
            return 0;
        }
        
        config.setModConfig(modId, mode, action, null);
        ctx.getSource().sendSuccess(() -> Component.literal("✓ Changed " + modId + " to " + mode + " with " + action).withStyle(ChatFormatting.GREEN), true);
        HandShakerServer.getInstance().checkAllPlayers();
        return Command.SINGLE_SUCCESS;
    }

    private static int removeMod(CommandContext<CommandSourceStack> ctx) {
        String modId = StringArgumentType.getString(ctx, "mod").toLowerCase();
        ConfigManager config = HandShakerServer.getInstance().getConfigManager();
        
        boolean removed = config.removeModConfig(modId);
        if (removed) {
            ctx.getSource().sendSuccess(() -> Component.literal("✓ Removed " + modId).withStyle(ChatFormatting.GREEN), true);
            HandShakerServer.getInstance().checkAllPlayers();
        } else {
            ctx.getSource().sendFailure(Component.literal("Mod not found: " + modId));
        }
        
        return removed ? Command.SINGLE_SUCCESS : 0;
    }

    private static int showInfo(CommandContext<CommandSourceStack> ctx) {
        ConfigManager config = HandShakerServer.getInstance().getConfigManager();
        PlayerHistoryDatabase db = HandShakerServer.getInstance().getPlayerHistoryDb();
        
        ctx.getSource().sendSystemMessage(Component.literal("═══════════════════════════════").withStyle(ChatFormatting.GOLD));
        ctx.getSource().sendSystemMessage(Component.literal("HandShaker Statistics").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        ctx.getSource().sendSystemMessage(Component.literal("═══════════════════════════════").withStyle(ChatFormatting.GOLD));
        
        int uniqueMods = 0;
        int activePlayers = 0;
        if (db != null && db.isEnabled()) {
            Map<String, Integer> popularity = db.getModPopularity();
            uniqueMods = popularity.size();
            activePlayers = db.getUniqueActivePlayers();
        }
        
        int configuredMods = config.getModConfigMap().size();
        
        ctx.getSource().sendSystemMessage(Component.literal("Unique Mods Detected: ").withStyle(ChatFormatting.YELLOW)
            .append(Component.literal(uniqueMods + "").withStyle(ChatFormatting.WHITE)));
        ctx.getSource().sendSystemMessage(Component.literal("Configured Mods: ").withStyle(ChatFormatting.YELLOW)
            .append(Component.literal(configuredMods + "").withStyle(ChatFormatting.WHITE)));
        if (db != null && db.isEnabled()) {
            ctx.getSource().sendSystemMessage(Component.literal("Active Players: ").withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(activePlayers + "").withStyle(ChatFormatting.WHITE)));
        }
        
        ctx.getSource().sendSystemMessage(Component.empty());
        ctx.getSource().sendSystemMessage(Component.literal("Use /handshaker info configured_mods to list configured mods").withStyle(ChatFormatting.GRAY));
        ctx.getSource().sendSystemMessage(Component.literal("Use /handshaker info all_mods [page] to see all detected mods").withStyle(ChatFormatting.GRAY));
        
        return Command.SINGLE_SUCCESS;
    }

    private static int showConfiguredMods(CommandContext<CommandSourceStack> ctx) {
        ConfigManager config = HandShakerServer.getInstance().getConfigManager();
        Map<String, ConfigManager.ModConfig> mods = config.getModConfigMap();
        
        ctx.getSource().sendSystemMessage(Component.literal("═══════════════════════════════").withStyle(ChatFormatting.GOLD));
        ctx.getSource().sendSystemMessage(Component.literal("Configured Mods (Whitelist: " + (config.isWhitelist() ? "ON" : "OFF") + ")").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        ctx.getSource().sendSystemMessage(Component.literal("═══════════════════════════════").withStyle(ChatFormatting.GOLD));
        
        if (mods.isEmpty()) {
            ctx.getSource().sendSystemMessage(Component.literal("No mods configured").withStyle(ChatFormatting.YELLOW));
            return Command.SINGLE_SUCCESS;
        }
        
        for (Map.Entry<String, ConfigManager.ModConfig> entry : mods.entrySet()) {
            ConfigManager.ModConfig modCfg = entry.getValue();
            String actionStr = !modCfg.getAction().toString().toLowerCase().equals("kick") ? " | " + modCfg.getAction() : "";
            
            ChatFormatting statusColor = switch (modCfg.getMode()) {
                case "required" -> ChatFormatting.GOLD;
                case "blacklisted" -> ChatFormatting.RED;
                default -> ChatFormatting.GREEN;
            };
            
            ctx.getSource().sendSystemMessage(Component.literal(entry.getKey()).withStyle(statusColor)
                .append(Component.literal(" | " + modCfg.getMode() + actionStr).withStyle(ChatFormatting.GRAY)));
        }
        
        return Command.SINGLE_SUCCESS;
    }

    private static int showAllMods(CommandContext<CommandSourceStack> ctx) {
        return showAllModsWithPage(ctx, 1);
    }

    private static int showAllModsWithPage(CommandContext<CommandSourceStack> ctx) {
        int page = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "page");
        return showAllModsWithPage(ctx, page);
    }

    private static int showAllModsWithPage(CommandContext<CommandSourceStack> ctx, int pageNum) {
        PlayerHistoryDatabase db = HandShakerServer.getInstance().getPlayerHistoryDb();
        ConfigManager config = HandShakerServer.getInstance().getConfigManager();
        
        if (db == null || !db.isEnabled()) {
            ctx.getSource().sendFailure(Component.literal("Player history database not available"));
            return 0;
        }
        
        Map<String, Integer> popularity = db.getModPopularity();
        List<Map.Entry<String, Integer>> sortedMods = new ArrayList<>(popularity.entrySet());
        sortedMods.sort((a, b) -> b.getValue().compareTo(a.getValue()));
        
        int pageSize = 10;
        int totalPages = (int) Math.ceil((double) sortedMods.size() / pageSize);
        
        if (pageNum < 1 || pageNum > totalPages) {
            ctx.getSource().sendFailure(Component.literal("Invalid page. Total pages: " + totalPages));
            return 0;
        }
        
        int startIdx = (pageNum - 1) * pageSize;
        int endIdx = Math.min(startIdx + pageSize, sortedMods.size());
        
        ctx.getSource().sendSystemMessage(Component.literal("═══════════════════════════════").withStyle(ChatFormatting.GOLD));
        ctx.getSource().sendSystemMessage(Component.literal("All Detected Mods (Page " + pageNum + "/" + totalPages + ")").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        ctx.getSource().sendSystemMessage(Component.literal("═══════════════════════════════").withStyle(ChatFormatting.GOLD));
        
        for (int i = startIdx; i < endIdx; i++) {
            Map.Entry<String, Integer> entry = sortedMods.get(i);
            ConfigManager.ModConfig modCfg = config.getModConfig(entry.getKey());
            
            // Format with color based on mode (color the mod name, not a status tag)
            ChatFormatting modeColor = ChatFormatting.GRAY; // default if not configured
            if (modCfg != null) {
                String mode = modCfg.getMode();
                if ("required".equals(mode)) {
                    modeColor = ChatFormatting.GOLD;
                } else if ("blacklisted".equals(mode)) {
                    modeColor = ChatFormatting.RED;
                } else if ("allowed".equals(mode)) {
                    modeColor = ChatFormatting.GREEN;
                }
            }
            
            ctx.getSource().sendSystemMessage(Component.literal(entry.getKey() + " (" + entry.getValue() + " players)").withStyle(modeColor));
        }
        
        if (pageNum < totalPages) {
            ctx.getSource().sendSystemMessage(Component.literal("Use /handshaker info all_mods " + (pageNum + 1) + " for next page").withStyle(ChatFormatting.GRAY));
        }
        
        return Command.SINGLE_SUCCESS;
    }

    private static int showModInfo(CommandContext<CommandSourceStack> ctx) {
        String modName = StringArgumentType.getString(ctx, "modName");
        PlayerHistoryDatabase db = HandShakerServer.getInstance().getPlayerHistoryDb();
        
        if (db == null || !db.isEnabled()) {
            ctx.getSource().sendFailure(Component.literal("Player history database not available"));
            return 0;
        }
        
        List<PlayerHistoryDatabase.PlayerModInfo> players = db.getPlayersWithMod(modName);
        
        if (players.isEmpty()) {
            ctx.getSource().sendSystemMessage(Component.literal("No players found with mod: " + modName).withStyle(ChatFormatting.YELLOW));
            return Command.SINGLE_SUCCESS;
        }
        
        ctx.getSource().sendSystemMessage(Component.literal("═══════════════════════════════").withStyle(ChatFormatting.GOLD));
        ctx.getSource().sendSystemMessage(Component.literal("Mod: " + modName).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        ctx.getSource().sendSystemMessage(Component.literal("═══════════════════════════════").withStyle(ChatFormatting.GOLD));
        ctx.getSource().sendSystemMessage(Component.literal("Players: " + players.size()).withStyle(ChatFormatting.YELLOW));
        ctx.getSource().sendSystemMessage(Component.empty());
        
        for (PlayerHistoryDatabase.PlayerModInfo player : players) {
            String status = player.isActive() ? "✓ Active" : "✗ Removed";
            ChatFormatting statusColor = player.isActive() ? ChatFormatting.GREEN : ChatFormatting.RED;
            
            ctx.getSource().sendSystemMessage(Component.literal(player.currentName()).withStyle(ChatFormatting.AQUA)
                .append(Component.literal(" - " + status).withStyle(statusColor))
                .append(Component.literal(" (Since: " + player.getFirstSeenFormatted() + ")").withStyle(ChatFormatting.GRAY)));
        }
        
        return Command.SINGLE_SUCCESS;
    }

    private static int setMode(CommandContext<CommandSourceStack> ctx) {
        String listName = StringArgumentType.getString(ctx, "list").toLowerCase();
        String action = StringArgumentType.getString(ctx, "action").toLowerCase();
        ConfigManager config = HandShakerServer.getInstance().getConfigManager();

        if (!action.equals("on") && !action.equals("off")) {
            ctx.getSource().sendSuccess(() -> Component.literal("✗ Action must be 'on' or 'off'").withStyle(ChatFormatting.RED), true);
            return Command.SINGLE_SUCCESS;
        }

        boolean isActive = action.equals("on");
        String displayName;
        boolean newState;

        switch (listName) {
            case "mods_required" -> {
                newState = config.toggleRequiredModsActive();
                displayName = "Required Mods";
                if (isActive && config.getRequiredMods().isEmpty()) {
                    ctx.getSource().sendSystemMessage(Component.literal("⚠ Warning: No required mods configured in mods-required.yml").withStyle(ChatFormatting.YELLOW));
                }
            }
            case "mods_blacklisted" -> {
                newState = config.toggleBlacklistedModsActive();
                displayName = "Blacklisted Mods";
                if (isActive && config.getBlacklistedMods().isEmpty()) {
                    ctx.getSource().sendSystemMessage(Component.literal("⚠ Warning: No blacklisted mods configured in mods-blacklisted.yml").withStyle(ChatFormatting.YELLOW));
                }
            }
            case "mods_whitelisted" -> {
                newState = config.toggleWhitelistedModsActive();
                displayName = "Whitelisted Mods";
                if (isActive && config.getWhitelistedMods().isEmpty()) {
                    ctx.getSource().sendSystemMessage(Component.literal("⚠ Warning: No whitelisted mods configured in mods-whitelisted.yml").withStyle(ChatFormatting.YELLOW));
                }
            }
            default -> {
                ctx.getSource().sendSuccess(() -> Component.literal("✗ Unknown list: " + listName).withStyle(ChatFormatting.RED), true);
                ctx.getSource().sendSystemMessage(Component.literal("Available lists: mods_required, mods_blacklisted, mods_whitelisted").withStyle(ChatFormatting.GRAY));
                return Command.SINGLE_SUCCESS;
            }
        }

        final String finalDisplayName = displayName;
        final boolean finalNewState = newState;
        ctx.getSource().sendSuccess(() -> Component.literal("✓ " + finalDisplayName + " turned " + (finalNewState ? "ON" : "OFF")).withStyle(ChatFormatting.GREEN), true);
        HandShakerServer.getInstance().checkAllPlayers();
        return Command.SINGLE_SUCCESS;
    }

    private static boolean isValidMode(String mode) {
        return mode.equals("required") || mode.equals("blacklisted") || mode.equals("allowed");
    }

    private static boolean isValidAction(String action) {
        ConfigManager config = HandShakerServer.getInstance().getConfigManager();
        Set<String> availableActions = config.getAvailableActions();
        
        if (availableActions.isEmpty()) {
            // Fall back to default validation if none are configured
            return action.equals("kick") || action.equals("ban");
        }
        
        return availableActions.contains(action.toLowerCase());
    }

    // Suggestion methods
    private static CompletableFuture<Suggestions> suggestModes(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        return builder.suggest("required")
            .suggest("blacklisted")
            .suggest("allowed")
            .buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestActions(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        ConfigManager config = HandShakerServer.getInstance().getConfigManager();
        Set<String> availableActions = config.getAvailableActions();
        
        if (availableActions.isEmpty()) {
            // Fall back to default actions if none are configured
            return builder.suggest("kick")
                .suggest("ban")
                .buildFuture();
        }
        
        String remaining = builder.getRemaining().toLowerCase();
        for (String action : availableActions) {
            if (action.toLowerCase().startsWith(remaining)) {
                builder.suggest(action);
            }
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestMods(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) {
            return Suggestions.empty();
        }
        
        String remaining = builder.getRemaining().toLowerCase();
        // Always suggest * wildcard
        if ("*".startsWith(remaining)) {
            builder.suggest("*");
        }
        
        HandShakerServer.ClientInfo clientInfo = HandShakerServer.getInstance().getClients().get(player.getUUID());
        if (clientInfo != null) {
            for (String mod : clientInfo.mods()) {
                if (mod.toLowerCase().startsWith(remaining)) {
                    builder.suggest(mod);
                }
            }
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestConfiguredMods(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        ConfigManager config = HandShakerServer.getInstance().getConfigManager();
        for (String mod : config.getModConfigMap().keySet()) {
            if (mod.toLowerCase().startsWith(builder.getRemaining().toLowerCase())) {
                builder.suggest(mod);
            }
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestAllMods(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        PlayerHistoryDatabase db = HandShakerServer.getInstance().getPlayerHistoryDb();
        if (db == null || !db.isEnabled()) {
            return Suggestions.empty();
        }
        
        Map<String, Integer> allMods = db.getModPopularity();
        String remaining = builder.getRemaining().toLowerCase();
        for (String mod : allMods.keySet()) {
            if (mod.toLowerCase().startsWith(remaining)) {
                builder.suggest(mod);
            }
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestModeLists(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        return builder.suggest("mods_required")
            .suggest("mods_blacklisted")
            .suggest("mods_whitelisted")
            .buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestCurrentModeState(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        String listName = StringArgumentType.getString(ctx, "list").toLowerCase();
        ConfigManager config = HandShakerServer.getInstance().getConfigManager();
        
        boolean isCurrentlyEnabled = switch (listName) {
            case "mods_required" -> config.areModsRequiredEnabled();
            case "mods_blacklisted" -> config.areModsBlacklistedEnabled();
            case "mods_whitelisted" -> config.areModsWhitelistedEnabled();
            default -> false;
        };
        
        // Only suggest the opposite of the current state
        if (isCurrentlyEnabled) {
            builder.suggest("off");
        } else {
            builder.suggest("on");
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestPlayers(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        if (!(ctx.getSource().getLevel() instanceof net.minecraft.server.level.ServerLevel world)) {
            return Suggestions.empty();
        }
        for (ServerPlayer player : world.players()) {
            String name = player.getName().getString();
            if (name.toLowerCase().startsWith(builder.getRemaining().toLowerCase())) {
                builder.suggest(name);
            }
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestPlayerMods(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        String playerName = StringArgumentType.getString(ctx, "player");
        ServerPlayer player = ctx.getSource().getServer().getPlayerList().getPlayer(playerName);
        
        if (player == null) {
            return Suggestions.empty();
        }
        
        HandShakerServer.ClientInfo info = HandShakerServer.getInstance().getClients().get(player.getUUID());
        if (info == null || info.mods().isEmpty()) {
            return Suggestions.empty();
        }
        
        for (String mod : info.mods()) {
            if (mod.toLowerCase().startsWith(builder.getRemaining().toLowerCase())) {
                builder.suggest(mod);
            }
        }
        return builder.buildFuture();
    }

    private static int showPlayerMods(CommandContext<CommandSourceStack> ctx) {
        String playerName = StringArgumentType.getString(ctx, "player");
        ServerPlayer player = ctx.getSource().getServer().getPlayerList().getPlayer(playerName);
        
        if (player == null) {
            ctx.getSource().sendFailure(Component.literal("Player '" + playerName + "' not found"));
            return 0;
        }
        
        HandShakerServer.ClientInfo info = HandShakerServer.getInstance().getClients().get(player.getUUID());
        if (info == null || info.mods().isEmpty()) {
            ctx.getSource().sendSystemMessage(Component.literal("No mod list found for " + playerName).withStyle(ChatFormatting.YELLOW));
            return Command.SINGLE_SUCCESS;
        }
        
        ctx.getSource().sendSystemMessage(Component.literal("═══════════════════════════════").withStyle(ChatFormatting.GOLD));
        ctx.getSource().sendSystemMessage(Component.literal(playerName + "'s Mods").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        ctx.getSource().sendSystemMessage(Component.literal("═══════════════════════════════").withStyle(ChatFormatting.GOLD));
        
        for (String mod : info.mods()) {
            ctx.getSource().sendSystemMessage(Component.literal("  • " + mod).withStyle(ChatFormatting.WHITE));
        }
        
        return Command.SINGLE_SUCCESS;
    }

    private static int setPlayerModStatus(CommandContext<CommandSourceStack> ctx) {
        String playerName = StringArgumentType.getString(ctx, "player");
        String modId = StringArgumentType.getString(ctx, "mod").toLowerCase();
        String mode = StringArgumentType.getString(ctx, "mode").toLowerCase();
        
        ServerPlayer player = ctx.getSource().getServer().getPlayerList().getPlayer(playerName);
        if (player == null) {
            ctx.getSource().sendFailure(Component.literal("Player '" + playerName + "' not found"));
            return 0;
        }
        
        HandShakerServer.ClientInfo info = HandShakerServer.getInstance().getClients().get(player.getUUID());
        if (info == null || info.mods().isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("Player has no mods"));
            return 0;
        }
        
        if (!isValidMode(mode)) {
            ctx.getSource().sendFailure(Component.literal("Invalid mode. Use: required, blacklisted, or allowed"));
            return 0;
        }
        
        ConfigManager config = HandShakerServer.getInstance().getConfigManager();
        
        if (modId.equals("*")) {
            // Set all mods for this player
            int updated = 0;
            for (String mod : info.mods()) {
                if (!config.isIgnored(mod)) {
                    config.setModConfig(mod, mode, "kick", null);
                    updated++;
                }
            }
            final int finalUpdated = updated;
            ctx.getSource().sendSuccess(() -> Component.literal("✓ Set " + finalUpdated + " mods to " + mode + " for player " + playerName).withStyle(ChatFormatting.GREEN), true);
        } else {
            if (!info.mods().contains(modId)) {
                ctx.getSource().sendFailure(Component.literal("Player " + playerName + " does not have mod: " + modId));
                return 0;
            }
            config.setModConfig(modId, mode, "kick", null);
            ctx.getSource().sendSuccess(() -> Component.literal("✓ Set " + modId + " to " + mode + " for " + playerName).withStyle(ChatFormatting.GREEN), true);
        }
        
        HandShakerServer.getInstance().checkAllPlayers();
        return Command.SINGLE_SUCCESS;
    }

    private static int addIgnore(CommandContext<CommandSourceStack> ctx) {
        // Try to get mod from argument, fallback to "*" if it's a literal
        String modId;
        try {
            modId = StringArgumentType.getString(ctx, "mod").trim().toLowerCase();
        } catch (IllegalArgumentException e) {
            // mod was a literal("*"), so just use "*"
            modId = "*";
        }
        
        ConfigManager config = HandShakerServer.getInstance().getConfigManager();
        
        if (modId.equals("*")) {
            ServerPlayer player = ctx.getSource().getEntity() instanceof ServerPlayer ? (ServerPlayer) ctx.getSource().getEntity() : null;
            if (player == null) {
                ctx.getSource().sendFailure(Component.literal("Only players can use * wildcard"));
                return 0;
            }
            
            HandShakerServer.ClientInfo info = HandShakerServer.getInstance().getClients().get(player.getUUID());
            if (info == null || info.mods().isEmpty()) {
                ctx.getSource().sendFailure(Component.literal("No mods found on your client"));
                return 0;
            }
            
            int added = 0;
            for (String mod : info.mods()) {
                if (config.addIgnoredMod(mod)) {
                    added++;
                }
            }
            final int finalAdded = added;
            ctx.getSource().sendSuccess(() -> Component.literal("✓ Added " + finalAdded + " of your mods to ignore list").withStyle(ChatFormatting.GREEN), true);
        } else {
            final String finalModId = modId;
            boolean added = config.addIgnoredMod(modId);
            if (added) {
                ctx.getSource().sendSuccess(() -> Component.literal("✓ Added " + finalModId + " to ignore list").withStyle(ChatFormatting.GREEN), true);
            } else {
                ctx.getSource().sendSystemMessage(Component.literal("⚠ " + finalModId + " already in ignore list").withStyle(ChatFormatting.YELLOW));
            }
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int removeIgnore(CommandContext<CommandSourceStack> ctx) {
        String modId = StringArgumentType.getString(ctx, "mod").toLowerCase();
        ConfigManager config = HandShakerServer.getInstance().getConfigManager();
        
        boolean removed = config.removeIgnoredMod(modId);
        if (removed) {
            ctx.getSource().sendSuccess(() -> Component.literal("✓ Removed " + modId + " from ignore list").withStyle(ChatFormatting.GREEN), true);
        } else {
            ctx.getSource().sendSystemMessage(Component.literal("⚠ " + modId + " not in ignore list").withStyle(ChatFormatting.YELLOW));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int listIgnore(CommandContext<CommandSourceStack> ctx) {
        ConfigManager config = HandShakerServer.getInstance().getConfigManager();
        Set<String> ignored = config.getIgnoredMods();
        
        if (ignored.isEmpty()) {
            ctx.getSource().sendSystemMessage(Component.literal("No mods in ignore list").withStyle(ChatFormatting.YELLOW));
            return Command.SINGLE_SUCCESS;
        }
        
        ctx.getSource().sendSystemMessage(Component.literal("═══════════════════════════════").withStyle(ChatFormatting.GOLD));
        ctx.getSource().sendSystemMessage(Component.literal("Ignored Mods").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        ctx.getSource().sendSystemMessage(Component.literal("═══════════════════════════════").withStyle(ChatFormatting.GOLD));
        
        for (String mod : ignored) {
            ctx.getSource().sendSystemMessage(Component.literal("  • " + mod).withStyle(ChatFormatting.GRAY));
        }
        
        return Command.SINGLE_SUCCESS;
    }

    private static CompletableFuture<Suggestions> suggestIgnoredMods(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        ConfigManager config = HandShakerServer.getInstance().getConfigManager();
        for (String mod : config.getIgnoredMods()) {
            if (mod.toLowerCase().startsWith(builder.getRemaining().toLowerCase())) {
                builder.suggest(mod);
            }
        }
        return builder.buildFuture();
    }
}
