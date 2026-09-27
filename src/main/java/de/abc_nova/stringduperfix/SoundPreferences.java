package de.abc_nova.stringduperfix;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

final class SoundPreferences implements AutoCloseable {
   private final JavaPlugin plugin;
   private final File file;
   private final YamlConfiguration yaml;
   private final ExecutorService writer;
   private final SoundSpec cycleOff;
   private final SoundSpec cycleOn;
   private final SoundSpec commandSuccess;
   private final SoundSpec commandError;
   private volatile boolean enabled;

   private SoundPreferences(JavaPlugin plugin, File file, YamlConfiguration yaml) {
      this.plugin = plugin;
      this.file = file;
      this.yaml = yaml;
      this.enabled = yaml.getBoolean("enabled", true);
      this.cycleOff = read(yaml, "cycle.off", Sound.BLOCK_TRIPWIRE_CLICK_OFF, 0.62F, 0.72F, plugin);
      this.cycleOn = read(yaml, "cycle.on", Sound.BLOCK_TRIPWIRE_CLICK_ON, 0.56F, 0.82F, plugin);
      this.commandSuccess = read(yaml, "command.success", Sound.UI_BUTTON_CLICK, 0.65F, 1.45F, plugin);
      this.commandError = read(yaml, "command.error", Sound.BLOCK_NOTE_BLOCK_BASS, 0.65F, 0.70F, plugin);
      this.writer = Executors.newSingleThreadExecutor(runnable -> {
         Thread thread = new Thread(runnable, "string-duper-sounds-writer");
         thread.setDaemon(true);
         return thread;
      });
   }

   static SoundPreferences load(JavaPlugin plugin) {
      File file = new File(plugin.getDataFolder(), "sounds.yml");
      return new SoundPreferences(plugin, file, YamlConfiguration.loadConfiguration(file));
   }

   boolean enabled() {
      return enabled;
   }

   void setEnabled(boolean enabled) {
      this.enabled = enabled;
      yaml.set("enabled", enabled);
      String serialized = yaml.saveToString();
      writer.execute(() -> writeAtomically(serialized));
   }

   void playCycleOff(World world, Location location) {
      if (enabled) {
         cycleOff.play(world, location);
      }
   }

   void playCycleOn(World world, Location location) {
      if (enabled) {
         cycleOn.play(world, location);
      }
   }

   void playCommand(Player player, boolean success) {
      if (enabled) {
         (success ? commandSuccess : commandError).play(player.getWorld(), player.getLocation());
      }
   }

   private void writeAtomically(String serialized) {
      try {
         Files.createDirectories(file.toPath().getParent());
         var temporary = file.toPath().resolveSibling(file.getName() + ".tmp");
         Files.writeString(temporary, serialized, StandardCharsets.UTF_8);
         try {
            Files.move(temporary, file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
         } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
         }
      } catch (IOException exception) {
         plugin.getLogger().log(Level.SEVERE, "Could not save sounds.yml.", exception);
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

   @Override
   public void close() {
      writer.shutdown();
      try {
         if (!writer.awaitTermination(2, TimeUnit.SECONDS)) {
            plugin.getLogger().warning("The sounds.yml writer did not stop within two seconds.");
         }
      } catch (InterruptedException exception) {
         Thread.currentThread().interrupt();
      }
   }

   private record SoundSpec(Sound sound, float volume, float pitch) {
      void play(World world, Location location) {
         world.playSound(location, sound, volume, pitch);
      }
   }
}
