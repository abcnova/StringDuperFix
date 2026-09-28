package de.abc_nova.stringduperfix;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

final class ReleaseRegressionTest {
   private static String read(String relative) throws Exception {
      return Files.readString(Path.of(relative), StandardCharsets.UTF_8);
   }

   @Test
   void confirmedPistonlessBehaviorIsPackaged() throws Exception {
      String source = read("src/main/java/de/abc_nova/stringduperfix/VanillaMechanicsFeature.java");
      assertTrue(source.contains("active-when-lever-powered"));
      assertTrue(source.contains("collectionLocations"));
      assertTrue(source.contains("Material.HOPPER"));
      assertTrue(source.contains("new Vector(0.0, -0.08, 0.0)"));
      assertTrue(source.contains("sounds.playCycleOff"));
      assertTrue(source.contains("sounds.playCycleOn"));
      assertFalse(source.contains("dropItemNaturally"));
      assertFalse(source.contains("BlockPistonExtendEvent"));
   }

   @Test
   void safeDefaultsMatchTheDocumentedPistonlessSetup() throws Exception {
      String config = read("src/main/resources/config.yml");
      assertTrue(config.contains("active-when-lever-powered: false"));
      assertTrue(config.contains("interval-ticks: 6"));
      assertTrue(config.contains("attribute-swap:\n  enabled: false"));

      String sounds = read("src/main/resources/sounds.yml");
      assertTrue(sounds.contains("block.tripwire.click_off"));
      assertTrue(sounds.contains("pitch: 0.72"));
   }

   @Test
   void publishableCommandAndLanguageResourcesArePackaged() throws Exception {
      String plugin = read("src/main/resources/plugin.yml");
      String main = read("src/main/java/de/abc_nova/stringduperfix/StringDuperFixPlugin.java");
      String messages = read("src/main/resources/messages_de.yml");
      assertTrue(plugin.contains("stringduper:"));
      assertTrue(plugin.contains("version: 1.0.0"));
      assertTrue(plugin.contains("author: ABC_Nova"));
      assertTrue(main.contains("setSounds(player, !sounds.enabled(player))"));
      assertTrue(main.contains("sender.hasPermission(\"stringduper.admin\")"));
      assertTrue(main.contains("setMechanics(sender"));
      assertTrue(main.contains("List.of(\"on\", \"off\", \"status\")"));
      assertTrue(messages.contains("sounds-enabled:"));
   }

   @Test
   void allShippedYamlFilesAreSyntacticallyValid() throws Exception {
      Yaml yaml = new Yaml();
      for (String resource : new String[]{
         "plugin.yml", "config.yml", "messages_en.yml", "messages_de.yml",
         "messages_fr.yml", "messages_es.yml", "sounds.yml"
      }) {
         yaml.load(read("src/main/resources/" + resource));
      }

      Map<?, ?> english = yaml.load(read("src/main/resources/messages_en.yml"));
      for (String locale : new String[]{"de", "fr", "es"}) {
         Map<?, ?> translated = yaml.load(read("src/main/resources/messages_" + locale + ".yml"));
         assertEquals(english.keySet(), translated.keySet(), "Missing translation keys for " + locale);
      }
   }

   @Test
   void reloadReplacesListenersAndAllTranslationsArePackaged() throws Exception {
      String main = read("src/main/java/de/abc_nova/stringduperfix/StringDuperFixPlugin.java");
      String feature = read("src/main/java/de/abc_nova/stringduperfix/VanillaMechanicsFeature.java");
      String messages = read("src/main/java/de/abc_nova/stringduperfix/Messages.java");
      assertTrue(main.contains("args[0].equalsIgnoreCase(\"reload\")"));
      assertTrue(feature.contains("HandlerList.unregisterAll(this)"));
      assertTrue(messages.contains("messages_en.yml"));
      assertTrue(messages.contains("messages_de.yml"));
      assertTrue(messages.contains("messages_fr.yml"));
      assertTrue(messages.contains("messages_es.yml"));
      assertTrue(messages.contains("FALLBACK_LANGUAGE = \"en\""));
      String soundPreferences = read("src/main/java/de/abc_nova/stringduperfix/SoundPreferences.java");
      assertTrue(soundPreferences.contains("PersistentDataType.BYTE"));
      assertTrue(soundPreferences.contains("player.playSound"));
      assertFalse(soundPreferences.contains("world.playSound"));
      assertTrue(read("src/main/resources/messages_en.yml").contains("usage:"));
      assertTrue(read("src/main/resources/messages_fr.yml").contains("usage:"));
      assertTrue(read("src/main/resources/messages_es.yml").contains("usage:"));
   }
}
