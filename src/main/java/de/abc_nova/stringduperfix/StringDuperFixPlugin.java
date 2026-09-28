package de.abc_nova.stringduperfix;

import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class StringDuperFixPlugin extends JavaPlugin implements CommandExecutor, TabCompleter {
   private SoundPreferences sounds;
   private Messages messages;
   private VanillaMechanicsFeature mechanics;

   @Override
   public void onEnable() {
      saveBundledResources();
      loadRuntime();

      PluginCommand command = getCommand("stringduper");
      if (command == null) {
         throw new IllegalStateException("The /stringduper command is missing from plugin.yml.");
      }
      command.setExecutor(this);
      command.setTabCompleter(this);
      getLogger().info("String Duper Fix " + getPluginMeta().getVersion() + " is active.");
   }

   @Override
   public void onDisable() {
      if (mechanics != null) {
         mechanics.close();
      }
   }

   @Override
   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      if (args.length >= 1 && args[0].equalsIgnoreCase("sounds")) {
         handleSounds(sender, args);
         return true;
      }
      if (!sender.hasPermission("stringduper.admin")) {
         messages.send(sender, "no-permission");
         playCommandSound(sender, false);
         return true;
      }
      if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
         reloadPlugin(sender);
         return true;
      }
      if (args.length == 1 && (args[0].equalsIgnoreCase("on") || args[0].equalsIgnoreCase("off"))) {
         setMechanics(sender, args[0].equalsIgnoreCase("on"));
         return true;
      }
      if (args.length == 1 && args[0].equalsIgnoreCase("status")) {
         messages.send(sender, mechanics.enabled() ? "status-on" : "status-off");
         playCommandSound(sender, true);
         return true;
      }
      if (args.length < 1) {
         messages.send(sender, "usage");
         playCommandSound(sender, false);
         return true;
      }
      messages.send(sender, "usage");
      playCommandSound(sender, false);
      return true;
   }

   private void handleSounds(CommandSender sender, String[] args) {
      if (!(sender instanceof Player player)) {
         messages.send(sender, "player-only");
         return;
      }
      if (args.length == 1) {
         setSounds(player, !sounds.enabled(player));
         return;
      }
      if (args.length != 2) {
         messages.send(sender, "usage");
         playCommandSound(sender, false);
         return;
      }
      String mode = args[1].toLowerCase(Locale.ROOT);
      if (mode.equals("status")) {
         if (!sender.hasPermission("stringduper.admin")) {
            messages.send(sender, "no-permission");
            playCommandSound(sender, false);
            return;
         }
         messages.send(sender, sounds.enabled(player) ? "sounds-status-on" : "sounds-status-off");
         playCommandSound(sender, true);
         return;
      }
      if (mode.equals("on") || mode.equals("off")) {
         setSounds(player, mode.equals("on"));
         return;
      }
      messages.send(sender, "usage");
      playCommandSound(sender, false);
   }

   private void reloadPlugin(CommandSender sender) {
      try {
         loadRuntime();
         messages.send(sender, "reload-success");
         playCommandSound(sender, true);
      } catch (RuntimeException exception) {
         getLogger().log(Level.SEVERE, "Could not reload String Duper Fix.", exception);
         if (messages != null) {
            messages.send(sender, "reload-failed");
         }
      }
   }

   private void saveBundledResources() {
      saveResource("config.yml", false);
      saveResource("sounds.yml", false);
      saveResource("messages_en.yml", false);
      saveResource("messages_de.yml", false);
      saveResource("messages_fr.yml", false);
      saveResource("messages_es.yml", false);
   }

   private void loadRuntime() {
      SoundPreferences newSounds = SoundPreferences.load(this);
      try {
         Messages newMessages = Messages.load(this);
         VanillaMechanicsFeature newMechanics = VanillaMechanicsFeature.install(this, newSounds);
         VanillaMechanicsFeature oldMechanics = mechanics;
         mechanics = newMechanics;
         sounds = newSounds;
         messages = newMessages;
         if (oldMechanics != null) {
            oldMechanics.close();
         }
      } catch (RuntimeException exception) {
         throw exception;
      }
   }

   private void setSounds(Player player, boolean enabled) {
      sounds.setEnabled(player, enabled);
      messages.send(player, enabled ? "sounds-enabled" : "sounds-disabled");
      if (enabled) {
         sounds.playCommand(player, true);
      }
   }

   private void setMechanics(CommandSender sender, boolean enabled) {
      mechanics.setEnabled(enabled);
      messages.send(sender, enabled ? "enabled" : "disabled");
      playCommandSound(sender, true);
   }

   private void playCommandSound(CommandSender sender, boolean success) {
      if (sender instanceof Player player) {
         sounds.playCommand(player, success);
      }
   }

   @Override
   public List<String> onTabComplete(
      CommandSender sender,
      Command command,
      String alias,
      String[] args
   ) {
      if (args.length == 1) {
         List<String> options = sender.hasPermission("stringduper.admin")
            ? List.of("on", "off", "status", "sounds", "reload")
            : List.of("sounds");
         return prefixMatches(args[0], options);
      }
      if (args.length == 2 && args[0].equalsIgnoreCase("sounds")) {
         List<String> options = sender.hasPermission("stringduper.admin")
            ? List.of("on", "off", "status")
            : List.of("on", "off");
         return prefixMatches(args[1], options);
      }
      return List.of();
   }

   private List<String> prefixMatches(String input, List<String> values) {
      String prefix = input.toLowerCase(Locale.ROOT);
      return values.stream().filter(value -> value.startsWith(prefix)).toList();
   }
}
