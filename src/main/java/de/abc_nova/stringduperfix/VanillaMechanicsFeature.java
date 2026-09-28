package de.abc_nova.stringduperfix;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.AnaloguePowerable;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Powerable;
import org.bukkit.block.data.type.Tripwire;
import org.bukkit.block.data.type.TripwireHook;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.HandlerList;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

/**
 * Restores selected vanilla mechanics that Paper/Leaf intentionally guard.
 *
 * <p>The string-duper path is event-driven. It remembers only complete,
 * correctly oriented hook-to-hook lines and does no permanent world scan.
 * A one-tick task exists only while a nearby lever remains in the configured
 * active state.</p>
 */
final class VanillaMechanicsFeature implements Listener {
   private static final List<BlockFace> CARDINAL = List.of(
      BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST
   );

   private final JavaPlugin plugin;
   private final MechanicsSettings settings;
   private final SoundPreferences sounds;
   private final Map<String, Integer> lastActivationTick = new HashMap<>();
   private final Map<String, TripwireLine> knownLines = new HashMap<>();
   private final Set<String> pendingChecks = new HashSet<>();
   private final Map<String, Location> poweredSources = new HashMap<>();
   private final Map<String, TripwireLine> poweredLines = new HashMap<>();
   private final Map<String, Set<String>> lineSources = new HashMap<>();
   private final Map<String, Integer> nextPoweredOutputTick = new HashMap<>();
   private final Map<String, Location> collectionLocations = new HashMap<>();
   private final File settingsFile;
   private final YamlConfiguration settingsYaml;
   private final ExecutorService settingsWriter;
   private volatile boolean enabled;
   private BukkitTask poweredTask;

   private VanillaMechanicsFeature(
      JavaPlugin plugin,
      MechanicsSettings settings,
      SoundPreferences sounds,
      File settingsFile,
      YamlConfiguration settingsYaml
   ) {
      this.plugin = plugin;
      this.settings = settings;
      this.sounds = sounds;
      this.settingsFile = settingsFile;
      this.settingsYaml = settingsYaml;
      this.enabled = settings.stringDuperEnabled();
      this.settingsWriter = Executors.newSingleThreadExecutor(runnable -> {
         Thread thread = new Thread(runnable, "string-duper-settings-writer");
         thread.setDaemon(true);
         return thread;
      });
   }

   static VanillaMechanicsFeature install(JavaPlugin plugin, SoundPreferences sounds) {
      File settingsFile = new File(plugin.getDataFolder(), "config.yml");
      if (!settingsFile.exists()) {
         plugin.saveResource("config.yml", false);
      }

      YamlConfiguration settings = YamlConfiguration.loadConfiguration(settingsFile);
      migrateSettings(plugin, settingsFile, settings);
      VanillaMechanicsFeature feature = new VanillaMechanicsFeature(
         plugin,
         MechanicsSettings.from(settings),
         sounds,
         settingsFile,
         settings
      );
      File serverRoot = plugin.getDataFolder().getParentFile().getParentFile();
      boolean restartRequired = false;
      if (feature.settings.attributeSwapEnabled()) {
         restartRequired |= replace(
            plugin,
            new File(serverRoot, "config/paper-global.yml"),
            "update-equipment-on-player-actions:",
            "  update-equipment-on-player-actions: false"
         );
      }
      if (feature.settings.stringDuperEnabled()) {
         restartRequired |= replace(
            plugin,
            new File(serverRoot, "config/paper-global.yml"),
            "skip-tripwire-hook-placement-validation:",
            "  skip-tripwire-hook-placement-validation: true"
         );
         File leafConfig = new File(serverRoot, "config/leaf-global.yml");
         if (leafConfig.isFile()) {
            restartRequired |= replace(
               plugin,
               leafConfig,
               "allow-tripwire-dupe:",
               "  allow-tripwire-dupe: true"
            );
         }
      }

      if (restartRequired) {
         plugin.getLogger().warning(
            "Vanilla mechanics settings were corrected. One additional full restart is required before the server-level behavior is active."
         );
      } else {
         plugin.getLogger().info("Attribute swapping and the configured string/tripwire mechanics are enabled.");
      }
      if (feature.fallbackEnabled()) {
         plugin.getLogger().info("Event-driven tripwire-duper compatibility is enabled.");
      }
      Bukkit.getPluginManager().registerEvents(feature, plugin);
      return feature;
   }

   @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
   public void interact(PlayerInteractEvent event) {
      if (!fallbackEnabled() || event.getClickedBlock() == null) {
         return;
      }

      Block clicked = event.getClickedBlock();
      if (event.getAction() == Action.LEFT_CLICK_BLOCK && clicked.getType() == Material.TRIPWIRE) {
         if (event.getPlayer().getInventory().getItemInMainHand().getType() == Material.SHEARS
            && clicked.getBlockData() instanceof Tripwire wire
            && !wire.isDisarmed()) {
            wire.setDisarmed(true);
            clicked.setBlockData(wire, true);
            event.setCancelled(true);
            event.getPlayer().playSound(clicked.getLocation(), Sound.BLOCK_TRIPWIRE_CLICK_ON, 0.45F, 1.15F);
         }
         rememberWireLine(clicked);
         triggerWire(clicked);
         return;
      }

      if (event.getAction() == Action.RIGHT_CLICK_BLOCK && isControl(clicked)) {
         Bukkit.getScheduler().runTask(plugin, () -> {
            triggerNear(clicked);
            if (isPersistentControl(clicked)) {
               updatePoweredSource(clicked, isPowered(clicked));
            }
         });
      }
   }

   @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
   public void waterFlow(BlockFromToEvent event) {
      if (!fallbackEnabled() || event.getBlock().getType() != Material.WATER) {
         return;
      }
      Block destination = event.getToBlock();
      if (destination.getType() == Material.TRIPWIRE) {
         rememberWireLine(destination);
         triggerWire(destination);
      }
   }

   @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
   public void physics(BlockPhysicsEvent event) {
      if (!fallbackEnabled()) {
         return;
      }
      Block block = event.getBlock();
      if (block.getType() == Material.TRIPWIRE) {
         rememberWireLine(block);
         triggerWire(block);
      } else if (block.getType() == Material.TRIPWIRE_HOOK) {
         triggerHook(block);
      }
   }

   @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
   public void place(BlockPlaceEvent event) {
      if (!fallbackEnabled()) {
         return;
      }
      Material type = event.getBlockPlaced().getType();
      if (type == Material.TRIPWIRE || type == Material.TRIPWIRE_HOOK) {
         Bukkit.getScheduler().runTask(plugin, () -> rememberNear(event.getBlockPlaced()));
      }
   }

   @EventHandler(priority = EventPriority.MONITOR)
   public void redstone(BlockRedstoneEvent event) {
      if (!fallbackEnabled()
         || event.getOldCurrent() == event.getNewCurrent()
         || !isPersistentControl(event.getBlock())) {
         return;
      }
      Block source = event.getBlock();
      triggerNear(source);
      updatePoweredSource(source, event.getNewCurrent() > 0);
   }

   private boolean fallbackEnabled() {
      return enabled && settings.fallbackEnabled();
   }

   boolean enabled() {
      return enabled;
   }

   void setEnabled(boolean enabled) {
      this.enabled = enabled;
      if (!enabled) {
         clearRuntimeState();
      }
      settingsYaml.set("string-duper.enabled", enabled);
      String serialized = settingsYaml.saveToString();
      settingsWriter.execute(() -> writeSettings(serialized));
   }

   private void writeSettings(String serialized) {
      try {
         var target = settingsFile.toPath();
         var temporary = target.resolveSibling(target.getFileName() + ".tmp");
         Files.writeString(temporary, serialized, StandardCharsets.UTF_8);
         try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
         } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
         }
      } catch (IOException exception) {
         plugin.getLogger().log(Level.SEVERE, "Could not save config.yml.", exception);
      }
   }

   private void triggerNear(Block source) {
      for (TripwireLine line : linesNear(source)) {
         emulateAfterNativeUpdate(line);
      }
   }

   private Set<TripwireLine> linesNear(Block source) {
      LinkedHashSet<TripwireLine> result = new LinkedHashSet<>();
      int radius = activationScanRadius();
      int verticalRadius = Math.min(radius, 3);
      for (int y = -verticalRadius; y <= verticalRadius; y++) {
         for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
               Block candidate = source.getRelative(x, y, z);
               if (candidate.getType() == Material.TRIPWIRE_HOOK) {
                  for (TripwireLine line : connectedLines(candidate)) {
                     remember(line);
                     result.add(line);
                  }
               }
            }
         }
      }
      for (TripwireLine line : knownLines.values()) {
         if (sameWorld(line.center(), source.getLocation()) && distanceToLine(source.getLocation(), line) <= radius) {
            result.add(line);
         }
      }
      return result;
   }

   private void triggerWire(Block wireBlock) {
      Set<String> triggered = new HashSet<>();
      for (BlockFace direction : CARDINAL) {
         Block cursor = wireBlock;
         for (int distance = 1; distance <= maxLineLength(); distance++) {
            cursor = cursor.getRelative(direction);
            if (cursor.getType() == Material.TRIPWIRE) {
               continue;
            }
            if (cursor.getType() == Material.TRIPWIRE_HOOK) {
               for (TripwireLine line : connectedLines(cursor)) {
                  if (triggered.add(line.key())) {
                     remember(line);
                     emulateAfterNativeUpdate(line);
                  }
               }
            }
            break;
         }
      }
   }

   private void triggerHook(Block hook) {
      for (TripwireLine line : connectedLines(hook)) {
         remember(line);
         emulateAfterNativeUpdate(line);
      }
   }

   private void rememberWireLine(Block wireBlock) {
      if (wireBlock.getType() != Material.TRIPWIRE) {
         return;
      }
      for (BlockFace direction : CARDINAL) {
         Block cursor = wireBlock;
         for (int distance = 1; distance <= maxLineLength(); distance++) {
            cursor = cursor.getRelative(direction);
            if (cursor.getType() == Material.TRIPWIRE) {
               continue;
            }
            if (cursor.getType() == Material.TRIPWIRE_HOOK) {
               connectedLines(cursor).forEach(this::remember);
            }
            break;
         }
      }
   }

   private void rememberNear(Block block) {
      if (block.getType() == Material.TRIPWIRE) {
         rememberWireLine(block);
      } else if (block.getType() == Material.TRIPWIRE_HOOK) {
         connectedLines(block).forEach(this::remember);
      }
   }

   private void remember(TripwireLine line) {
      knownLines.put(line.key(), line);
   }

   private int maxLineLength() {
      return settings.maximumLineLength();
   }

   private List<TripwireLine> connectedLines(Block hook) {
      if (!(hook.getBlockData() instanceof TripwireHook hookData)) {
         return List.of();
      }
      TripwireLine line = connectedLine(hook, hookData.getFacing());
      return line == null ? List.of() : List.of(line);
   }

   private TripwireLine connectedLine(Block firstHook, BlockFace direction) {
      if (!CARDINAL.contains(direction)) {
         return null;
      }
      ArrayList<TripwireState> wire = new ArrayList<>();
      for (int distance = 1; distance <= maxLineLength() + 1; distance++) {
         Block candidate = firstHook.getRelative(direction, distance);
         if (candidate.getType() == Material.TRIPWIRE) {
            wire.add(new TripwireState(candidate.getLocation(), candidate.getBlockData().clone()));
            continue;
         }
         if (candidate.getType() != Material.TRIPWIRE_HOOK
            || wire.isEmpty()
            || !(candidate.getBlockData() instanceof TripwireHook secondHookData)
            || secondHookData.getFacing() != direction.getOppositeFace()) {
            return null;
         }
         String firstKey = blockKey(firstHook);
         String secondKey = blockKey(candidate);
         String key = firstKey.compareTo(secondKey) <= 0
            ? firstKey + "|" + secondKey
            : secondKey + "|" + firstKey;
         Location center = wire.get(wire.size() / 2).location().clone().add(0.5, 0.25, 0.5);
         return new TripwireLine(
            firstHook.getLocation(), candidate.getLocation(), List.copyOf(wire), center, key
         );
      }
      return null;
   }

   private void emulateAfterNativeUpdate(TripwireLine line) {
      int tick = Bukkit.getCurrentTick();
      int cooldown = settings.activationCooldownTicks();
      Integer previous = lastActivationTick.put(line.key(), tick);
      if (previous != null && tick - previous < cooldown) {
         return;
      }
      double radius = settings.nativeDropDetectionRadius();
      Map<UUID, Integer> existingStrings = nearbyStrings(line.center(), radius);
      long delay = settings.checkDelayTicks();
      int attempts = settings.checkWindowTicks();
      if (pendingChecks.add(line.key())) {
         Bukkit.getScheduler().runTaskLater(
            plugin,
            () -> checkCompatibilityWindow(line, existingStrings, radius, attempts),
            delay
         );
      }
   }

   private void checkCompatibilityWindow(
      TripwireLine line,
      Map<UUID, Integer> existingStrings,
      double radius,
      int attemptsLeft
   ) {
      if (!hooksRemain(line)) {
         forgetLine(line.key());
         return;
      }
      List<TripwireState> missing = missingWire(line);
      if (missing.isEmpty()) {
         if (attemptsLeft > 1) {
            Bukkit.getScheduler().runTaskLater(
               plugin,
               () -> checkCompatibilityWindow(line, existingStrings, radius, attemptsLeft - 1),
               1L
            );
         } else {
            pendingChecks.remove(line.key());
         }
         return;
      }

      List<Location> restored = new ArrayList<>(missing.size());
      for (TripwireState state : missing) {
         Block block = state.location().getBlock();
         Material current = block.getType();
         if (current.isAir() || current == Material.WATER) {
            block.setBlockData(state.data().clone(), false);
            restored.add(state.location());
         }
      }
      if (restored.isEmpty()) {
         pendingChecks.remove(line.key());
         return;
      }

      int perWire = settings.stringPerBrokenWire();
      int requestedOutput = Math.min(64, restored.size() * perWire);
      int nativeOutput = newStringAmount(existingStrings, nearbyStrings(line.center(), radius));
      int syntheticOutput = Math.max(0, requestedOutput - nativeOutput);
      dropString(line, syntheticOutput);
      playCycleSound(line);
      pendingChecks.remove(line.key());
   }

   private void updatePoweredSource(Block source, boolean powered) {
      String sourceKey = blockKey(source);
      boolean activeState = settings.activeWhenLeverPowered();
      if (!continuousPoweredOutputEnabled() || powered != activeState) {
         removePoweredSource(sourceKey);
         return;
      }
      poweredSources.put(sourceKey, source.getLocation());
      int startTick = Bukkit.getCurrentTick() + settings.startDelayTicks();
      for (TripwireLine line : linesNear(source)) {
         if (!hasDisarmedWire(line) || distanceToLine(source.getLocation(), line) > activationScanRadius()) {
            continue;
         }
         remember(line);
         poweredLines.put(line.key(), line);
         lineSources.computeIfAbsent(line.key(), ignored -> new HashSet<>()).add(sourceKey);
         nextPoweredOutputTick.putIfAbsent(line.key(), startTick);
      }
      ensurePoweredTask();
   }

   private boolean continuousPoweredOutputEnabled() {
      return settings.continuousOutputEnabled();
   }

   private void ensurePoweredTask() {
      if (!poweredLines.isEmpty() && (poweredTask == null || poweredTask.isCancelled())) {
         poweredTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickPoweredLines, 1L, 1L);
      }
   }

   private void tickPoweredLines() {
      for (Map.Entry<String, Location> entry : new ArrayList<>(poweredSources.entrySet())) {
         Location location = entry.getValue();
         World world = location.getWorld();
         if (world == null
            || !world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)
            || !isPersistentControl(location.getBlock())
            || isPowered(location.getBlock()) != settings.activeWhenLeverPowered()) {
            removePoweredSource(entry.getKey());
         }
      }

      int tick = Bukkit.getCurrentTick();
      int interval = settings.intervalTicks();
      for (Map.Entry<String, TripwireLine> entry : new ArrayList<>(poweredLines.entrySet())) {
         String key = entry.getKey();
         TripwireLine line = entry.getValue();
         Set<String> sources = lineSources.get(key);
         if (sources == null || sources.isEmpty() || !hooksRemain(line) || !hasDisarmedWire(line)) {
            forgetPoweredLine(key);
            continue;
         }
         if (tick < nextPoweredOutputTick.getOrDefault(key, tick)) {
            continue;
         }
         if (missingWire(line).isEmpty()) {
            dropString(line, poweredBatchSize());
            playCycleSound(line);
         } else {
            emulateAfterNativeUpdate(line);
         }
         nextPoweredOutputTick.put(key, tick + interval);
      }
      stopPoweredTaskIfIdle();
   }

   private int poweredBatchSize() {
      return settings.stringPerCycle();
   }

   private boolean isControl(Block block) {
      Material type = block.getType();
      String name = type.name();
      return type == Material.LEVER
         || type == Material.REDSTONE_WIRE
         || name.endsWith("BUTTON")
         || name.endsWith("TRAPDOOR")
         || name.endsWith("DOOR")
         || block.getBlockData() instanceof Powerable
         || block.getBlockData() instanceof AnaloguePowerable;
   }

   private boolean isPersistentControl(Block block) {
      return block.getType() == Material.LEVER;
   }

   private boolean isPowered(Block block) {
      BlockData data = block.getBlockData();
      if (data instanceof Powerable powerable && powerable.isPowered()) {
         return true;
      }
      if (data instanceof AnaloguePowerable analogue && analogue.getPower() > 0) {
         return true;
      }
      return block.isBlockPowered() || block.isBlockIndirectlyPowered();
   }

   private boolean hasDisarmedWire(TripwireLine line) {
      for (TripwireState state : line.wire()) {
         Block block = state.location().getBlock();
         if (block.getBlockData() instanceof Tripwire current && current.isDisarmed()) {
            return true;
         }
         if (state.data() instanceof Tripwire remembered && remembered.isDisarmed()) {
            return true;
         }
      }
      return false;
   }

   private void removePoweredSource(String sourceKey) {
      poweredSources.remove(sourceKey);
      for (Map.Entry<String, Set<String>> entry : new ArrayList<>(lineSources.entrySet())) {
         entry.getValue().remove(sourceKey);
         if (entry.getValue().isEmpty()) {
            forgetPoweredLine(entry.getKey());
         }
      }
      stopPoweredTaskIfIdle();
   }

   private void forgetPoweredLine(String key) {
      poweredLines.remove(key);
      lineSources.remove(key);
      nextPoweredOutputTick.remove(key);
   }

   private void stopPoweredTaskIfIdle() {
      if (poweredLines.isEmpty() && poweredTask != null) {
         poweredTask.cancel();
         poweredTask = null;
      }
   }

   private void forgetLine(String key) {
      knownLines.remove(key);
      pendingChecks.remove(key);
      lastActivationTick.remove(key);
      collectionLocations.remove(key);
      forgetPoweredLine(key);
   }

   private List<TripwireState> missingWire(TripwireLine line) {
      ArrayList<TripwireState> missing = new ArrayList<>();
      for (TripwireState state : line.wire()) {
         if (state.location().getBlock().getType() != Material.TRIPWIRE) {
            missing.add(state);
         }
      }
      return missing;
   }

   private void dropString(TripwireLine line, int amount) {
      if (amount <= 0) {
         return;
      }
      Location location = collectionLocation(line);
      World world = location.getWorld();
      if (world != null) {
         // One stacked item entity per cycle preserves the output amount while
         // avoiding entity bursts. Deterministic motion prevents the vanilla
         // random spread from throwing strings behind the trapdoors.
         Item dropped = world.dropItem(location, new ItemStack(Material.STRING, Math.min(64, amount)));
         dropped.setVelocity(new Vector(0.0, -0.08, 0.0));
      }
   }

   private Location collectionLocation(TripwireLine line) {
      Location center = line.center();
      World world = center.getWorld();
      if (world == null) {
         return center.clone();
      }
      Location cached = collectionLocations.get(line.key());
      if (cached != null && sameWorld(cached, center)) {
         World cachedWorld = cached.getWorld();
         if (cachedWorld != null
            && cachedWorld.isChunkLoaded(cached.getBlockX() >> 4, cached.getBlockZ() >> 4)
            && cached.getBlock().getRelative(BlockFace.DOWN).getType() == Material.HOPPER) {
            return cached.clone();
         }
         collectionLocations.remove(line.key());
      }
      Block nearestHopper = null;
      double nearestDistance = Double.MAX_VALUE;
      Block origin = center.getBlock();
      for (int y = -3; y <= 1; y++) {
         for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
               Block candidate = origin.getRelative(x, y, z);
               if (candidate.getType() != Material.HOPPER) {
                  continue;
               }
               double distance = candidate.getLocation().distanceSquared(center);
               if (distance < nearestDistance) {
                  nearestDistance = distance;
                  nearestHopper = candidate;
               }
            }
         }
      }
      if (nearestHopper != null) {
         Location output = nearestHopper.getLocation().add(0.5, 1.15, 0.5);
         collectionLocations.put(line.key(), output);
         return output.clone();
      }

      Set<String> sources = lineSources.get(line.key());
      if (sources != null) {
         for (String sourceKey : sources) {
            Location source = poweredSources.get(sourceKey);
            if (source == null || !sameWorld(source, center)) {
               continue;
            }
            double dx = center.getX() - (source.getX() + 0.5);
            double dz = center.getZ() - (source.getZ() + 0.5);
            double length = Math.max(0.001, Math.sqrt(dx * dx + dz * dz));
            return center.clone().add(dx / length * 1.25, 0.35, dz / length * 1.25);
         }
      }
      return center.clone().add(0.0, 0.35, 0.0);
   }

   private void playCycleSound(TripwireLine line) {
      World world = line.center().getWorld();
      if (world == null) {
         return;
      }
      sounds.playCycleOff(world, line.firstHook());
      Bukkit.getScheduler().runTaskLater(
         plugin,
         () -> {
            World currentWorld = line.center().getWorld();
            if (currentWorld != null && hooksRemain(line)) {
               sounds.playCycleOn(currentWorld, line.secondHook());
            }
         },
         1L
      );
   }

   private int newStringAmount(Map<UUID, Integer> before, Map<UUID, Integer> after) {
      int amount = 0;
      for (Map.Entry<UUID, Integer> entry : after.entrySet()) {
         amount += Math.max(0, entry.getValue() - before.getOrDefault(entry.getKey(), 0));
      }
      return amount;
   }

   private Map<UUID, Integer> nearbyStrings(Location center, double radius) {
      HashMap<UUID, Integer> strings = new HashMap<>();
      World world = center.getWorld();
      if (world == null) {
         return strings;
      }
      for (Entity entity : world.getNearbyEntities(center, radius, radius, radius)) {
         if (entity instanceof Item item && item.getItemStack().getType() == Material.STRING) {
            strings.put(item.getUniqueId(), item.getItemStack().getAmount());
         }
      }
      return strings;
   }

   private boolean hooksRemain(TripwireLine line) {
      return line.firstHook().getBlock().getType() == Material.TRIPWIRE_HOOK
         && line.secondHook().getBlock().getType() == Material.TRIPWIRE_HOOK;
   }

   private int activationScanRadius() {
      return settings.activationScanRadius();
   }

   void close() {
      HandlerList.unregisterAll(this);
      clearRuntimeState();
      settingsWriter.shutdown();
      try {
         if (!settingsWriter.awaitTermination(2L, TimeUnit.SECONDS)) {
            plugin.getLogger().warning("The config.yml writer did not stop within two seconds.");
         }
      } catch (InterruptedException exception) {
         Thread.currentThread().interrupt();
      }
   }

   private void clearRuntimeState() {
      if (poweredTask != null) {
         poweredTask.cancel();
         poweredTask = null;
      }
      lastActivationTick.clear();
      knownLines.clear();
      pendingChecks.clear();
      poweredSources.clear();
      poweredLines.clear();
      lineSources.clear();
      nextPoweredOutputTick.clear();
      collectionLocations.clear();
   }

   private double distanceToLine(Location source, TripwireLine line) {
      double minimum = Math.min(axisDistance(source, line.firstHook()), axisDistance(source, line.secondHook()));
      for (TripwireState state : line.wire()) {
         minimum = Math.min(minimum, axisDistance(source, state.location()));
      }
      return minimum;
   }

   private boolean sameWorld(Location first, Location second) {
      return first.getWorld() != null
         && second.getWorld() != null
         && first.getWorld().getUID().equals(second.getWorld().getUID());
   }

   private double axisDistance(Location first, Location second) {
      return Math.max(
         Math.max(Math.abs(first.getX() - second.getX()), Math.abs(first.getY() - second.getY())),
         Math.abs(first.getZ() - second.getZ())
      );
   }

   private String blockKey(Block block) {
      return block.getWorld().getUID() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
   }

   private static void migrateSettings(
      JavaPlugin plugin,
      File settingsFile,
      YamlConfiguration settings
   ) {
      int configVersion = settings.getInt("config-version", 1);
      if (configVersion >= 6) {
         return;
      }
      String root = "string-duper.compatibility-fallback.";
      settings.set("config-version", 6);
      settings.set(root + "activation-scan-radius",
         Math.max(8, settings.getInt(root + "activation-scan-radius", 8)));
      settings.set(root + "check-window-ticks",
         Math.max(10, settings.getInt(root + "check-window-ticks", 10)));
      if (!settings.contains(root + "string-per-broken-wire")) {
         settings.set(root + "string-per-broken-wire",
            settings.getInt(root + "string-per-activation", 1));
      }
      settings.set(root + "string-per-activation", null);
      settings.addDefault(root + "continuous-powered-output.enabled", true);
      settings.addDefault(root + "continuous-powered-output.active-when-lever-powered", false);
      if (configVersion < 4
         && settings.getInt(root + "continuous-powered-output.start-delay-ticks", 6) == 6) {
         settings.set(root + "continuous-powered-output.start-delay-ticks", 2);
      }
      if (configVersion < 4
         && settings.getInt(root + "continuous-powered-output.interval-ticks", 10) == 10) {
         settings.set(root + "continuous-powered-output.interval-ticks", 6);
      }
      settings.addDefault(root + "continuous-powered-output.start-delay-ticks", 2);
      settings.addDefault(root + "continuous-powered-output.interval-ticks", 6);
      settings.addDefault(root + "continuous-powered-output.string-per-cycle", 1);
      settings.set(root + "continuous-powered-output.sound", null);
      settings.options().copyDefaults(true);
      try {
         settings.save(settingsFile);
      } catch (IOException exception) {
         plugin.getLogger().log(Level.SEVERE, "Could not migrate " + settingsFile, exception);
      }
   }

   private static boolean replace(JavaPlugin plugin, File file, String key, String replacement) {
      if (!file.isFile()) {
         plugin.getLogger().warning("Mechanics configuration was not found: " + file);
         return false;
      }
      try {
         List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
         ArrayList<String> updated = new ArrayList<>(lines);
         for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            if (!line.trim().startsWith(key)) {
               continue;
            }
            String indentation = line.substring(0, line.indexOf(line.trim()));
            String newLine = indentation + replacement.trim();
            if (line.equals(newLine)) {
               return false;
            }
            updated.set(index, newLine);
            writeAtomically(file, updated);
            plugin.getLogger().info("Enabled mechanic setting " + key.substring(0, key.length() - 1) + " in " + file.getName() + ".");
            return true;
         }
         plugin.getLogger().warning("Mechanics setting is missing from " + file.getName() + ": " + key);
      } catch (IOException exception) {
         plugin.getLogger().log(Level.SEVERE, "Could not update " + file, exception);
      }
      return false;
   }

   private static void writeAtomically(File file, List<String> lines) throws IOException {
      var target = file.toPath();
      var temporary = target.resolveSibling(target.getFileName() + ".string-duper-fix.tmp");
      Files.write(temporary, lines, StandardCharsets.UTF_8);
      try {
         Files.move(
            temporary,
            target,
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING
         );
      } catch (AtomicMoveNotSupportedException ignored) {
         Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
      }
   }

   private record TripwireLine(
      Location firstHook,
      Location secondHook,
      List<TripwireState> wire,
      Location center,
      String key
   ) {
   }

   private record TripwireState(Location location, BlockData data) {
   }

   private record MechanicsSettings(
      boolean attributeSwapEnabled,
      boolean stringDuperEnabled,
      boolean fallbackEnabled,
      int activationScanRadius,
      int maximumLineLength,
      long checkDelayTicks,
      int checkWindowTicks,
      int activationCooldownTicks,
      double nativeDropDetectionRadius,
      int stringPerBrokenWire,
      boolean continuousOutputEnabled,
      boolean activeWhenLeverPowered,
      int startDelayTicks,
      int intervalTicks,
      int stringPerCycle
   ) {
      static MechanicsSettings from(YamlConfiguration yaml) {
         String root = "string-duper.compatibility-fallback.";
         return new MechanicsSettings(
            yaml.getBoolean("attribute-swap.enabled", false),
            yaml.getBoolean("string-duper.enabled", true),
            yaml.getBoolean(root + "enabled", true),
            clamp(yaml.getInt(root + "activation-scan-radius", 8), 1, 12),
            clamp(yaml.getInt(root + "maximum-line-length", 40), 1, 40),
            clamp(yaml.getLong(root + "check-delay-ticks", 1L), 1L, 20L),
            clamp(yaml.getInt(root + "check-window-ticks", 10), 1, 20),
            clamp(yaml.getInt(root + "activation-cooldown-ticks", 1), 1, 20),
            clamp(yaml.getDouble(root + "native-drop-detection-radius", 3.0), 1.0, 8.0),
            clamp(yaml.getInt(root + "string-per-broken-wire", 1), 1, 16),
            yaml.getBoolean(root + "continuous-powered-output.enabled", true),
            yaml.getBoolean(root + "continuous-powered-output.active-when-lever-powered", false),
            clamp(yaml.getInt(root + "continuous-powered-output.start-delay-ticks", 2), 1, 40),
            clamp(yaml.getInt(root + "continuous-powered-output.interval-ticks", 6), 2, 100),
            clamp(yaml.getInt(root + "continuous-powered-output.string-per-cycle", 1), 1, 16)
         );
      }

      private static int clamp(int value, int minimum, int maximum) {
         return Math.max(minimum, Math.min(maximum, value));
      }

      private static long clamp(long value, long minimum, long maximum) {
         return Math.max(minimum, Math.min(maximum, value));
      }

      private static double clamp(double value, double minimum, double maximum) {
         return Math.max(minimum, Math.min(maximum, value));
      }
   }
}
