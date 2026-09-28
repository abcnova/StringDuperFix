package de.abc_nova.stringduperfix;

import java.io.File;
import java.util.Locale;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Stores each player's sound choice in their persistent player data. Farm sounds are sent
 * directly to nearby players so disabling sounds never changes what other players hear.
 */
final class SoundPreferences {
   private static final double AUDIBLE_RADIUS = 32.0;

   private final NamespacedKey disabledKey;
   private final SoundSpec cycleOff;
   private final SoundSpec cycleOn;
   private final SoundSpec commandSuccess;
   private final SoundSpec commandError;

   private SoundPreferences(JavaPlugin plugin, YamlConfiguration yaml) {
      this.disabledKey = new NamespacedKey(plugin, "sounds-disabled");
      this.cycleOff = read(yaml, "cycle.off", Sound.BLOCK_TRIPWIRE_CLICK_OFF, 0.62F, 0.72F, plugin);
      this.cycleOn = read(yaml, "cycle.on", Sound.BLOCK_TRIPWIRE_CLICK_ON, 0.56F, 0.82F, plugin);
      this.commandSuccess = read(yaml, "command.success", Sound.UI_BUTTON_CLICK, 0.65F, 1.45F, plugin);
      this.commandError = read(yaml, "command.error", Sound.BLOCK_NOTE_BLOCK_BASS, 0.65F, 0.70F, plugin);
   }

   static SoundPreferences load(JavaPlugin plugin) {
      return new SoundPreferences(
         plugin,
         YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "sounds.yml"))
      );
   }

   boolean enabled(Player player) {
      return !player.getPersistentDataContainer().has(disabledKey, PersistentDataType.BYTE);
   }

   void setEnabled(Player player, boolean enabled) {
      if (enabled) {
         player.getPersistentDataContainer().remove(disabledKey);
      } else {
         player.getPersistentDataContainer().set(disabledKey, PersistentDataType.BYTE, (byte)1);
      }
   }

   void playCycleOff(World world, Location location) {
      for (Player player : world.getNearbyPlayers(location, AUDIBLE_RADIUS)) {
         if (enabled(player)) {
            cycleOff.play(player, location);
         }
      }
   }

   void playCycleOn(World world, Location location) {
      for (Player player : world.getNearbyPlayers(location, AUDIBLE_RADIUS)) {
         if (enabled(player)) {
            cycleOn.play(player, location);
         }
      }
   }

   void playCommand(Player player, boolean success) {
      if (enabled(player)) {
         (success ? commandSuccess : commandError).play(player, player.getLocation());
      }
   }

   private static SoundSpec read(
      YamlConfiguration yaml,
      String path,
      Sound fallback,
      float fallbackVolume,
      float fallbackPitch,
      JavaPlugin plugin
   ) {
      String configured = yaml.getString(path + ".name");
      Sound sound = configured == null
         ? fallback
         : Registry.SOUNDS.get(NamespacedKey.minecraft(configured.toLowerCase(Locale.ROOT)));
      if (sound == null) {
         plugin.getLogger().warning("Invalid sound in sounds.yml: " + path + ".name=" + configured);
         sound = fallback;
      }
      float volume = clamp((float)yaml.getDouble(path + ".volume", fallbackVolume), 0.0F, 4.0F);
      float pitch = clamp((float)yaml.getDouble(path + ".pitch", fallbackPitch), 0.5F, 2.0F);
      return new SoundSpec(sound, volume, pitch);
   }

   private static float clamp(float value, float minimum, float maximum) {
      return Math.max(minimum, Math.min(maximum, value));
   }

   private record SoundSpec(Sound sound, float volume, float pitch) {
      void play(Player player, Location location) {
         player.playSound(location, sound, volume, pitch);
      }
   }
}
