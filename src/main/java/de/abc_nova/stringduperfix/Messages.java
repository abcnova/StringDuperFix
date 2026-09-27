package de.abc_nova.stringduperfix;

import java.io.File;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

final class Messages {
   private static final String FALLBACK_LANGUAGE = "en";
   private static final Map<String, String> SUPPORTED_LANGUAGES = Map.of(
      "en", "messages_en.yml",
      "de", "messages_de.yml",
      "fr", "messages_fr.yml",
      "es", "messages_es.yml"
   );

   private final Map<String, Map<String, Component>> translations;

   private Messages(Map<String, Map<String, Component>> translations) {
      this.translations = Map.copyOf(translations);
   }

   static Messages load(JavaPlugin plugin) {
      HashMap<String, Map<String, Component>> loaded = new HashMap<>();
      for (Map.Entry<String, String> language : SUPPORTED_LANGUAGES.entrySet()) {
         loaded.put(language.getKey(), loadLanguage(plugin, language.getValue()));
      }
      return new Messages(loaded);
   }

   private static Map<String, Component> loadLanguage(JavaPlugin plugin, String resourceName) {
      YamlConfiguration yaml = YamlConfiguration.loadConfiguration(
         new File(plugin.getDataFolder(), resourceName)
      );
      MiniMessage mini = MiniMessage.miniMessage();
      String prefix = yaml.getString(
         "prefix",
         "<gradient:#00D4FF:#0066FF><bold>String Duper Fix</bold></gradient> <dark_gray>»</dark_gray>"
      );
      HashMap<String, Component> parsed = new HashMap<>();
      for (String key : yaml.getKeys(false)) {
         if (!key.equals("prefix") && yaml.isString(key)) {
            String value = yaml.getString(key, key).replace("{prefix}", prefix);
            parsed.put(key, mini.deserialize(value));
         }
      }
      return Map.copyOf(parsed);
   }

   void send(CommandSender sender, String key) {
      Map<String, Component> language = translations.get(languageOf(sender));
      Map<String, Component> fallback = translations.get(FALLBACK_LANGUAGE);
      Component message = language.get(key);
      if (message == null) {
         message = fallback.getOrDefault(key, Component.text(key));
      }
      sender.sendMessage(message);
   }

   private String languageOf(CommandSender sender) {
      if (!(sender instanceof Player player)) {
         return FALLBACK_LANGUAGE;
      }
      String locale = player.locale().getLanguage().toLowerCase(Locale.ROOT);
      return translations.containsKey(locale) ? locale : FALLBACK_LANGUAGE;
   }
}
