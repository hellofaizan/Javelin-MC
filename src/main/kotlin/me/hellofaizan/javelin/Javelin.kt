package me.hellofaizan.javelin

import com.destroystokyo.paper.event.player.PlayerLaunchProjectileEvent
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.*
import org.bukkit.entity.Trident
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.ProjectileHitEvent
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitRunnable
import java.io.File
import java.util.UUID
import org.json.simple.JSONArray
import org.json.simple.JSONObject
import org.json.simple.parser.JSONParser
import kotlin.math.sqrt

class Javelin : JavaPlugin(), Listener, CommandExecutor {

    private var referencePoint: Location? = null
    private val throwLocations = mutableMapOf<UUID, Location>()
    private val leaderboard = mutableMapOf<UUID, LeaderboardEntry>()
    private lateinit var leaderboardFile: File
    private var worldRecord: Double = 0.0

    data class LeaderboardEntry(val uuid: UUID, val name: String, var distance: Double)

    override fun onEnable() {
        // Plugin startup logic
        val world: World? = Bukkit.getWorld("world")
        if (world != null) {
            referencePoint = Location(world, 100.0, 64.0, 100.0)
        } else {
            logger.warning("The specified world could not be found. Make sure 'world' is loaded.")
        }
        saveDefaultConfig()
        Config.init()
        server.pluginManager.registerEvents(this, this)

        // Initialize leaderboard file
        leaderboardFile = File(dataFolder, "leaderboard.json")
        if (!leaderboardFile.exists()) {
            leaderboardFile.createNewFile()
        }
        loadLeaderboard()

        // Register command
        getCommand("javelinleaderboard")?.setExecutor(this)

        logger.info("Javelin has been enabled!")
    }

    @EventHandler
    fun onTridentThrow(event: PlayerLaunchProjectileEvent) {
        val projectile = event.projectile
        val player = event.player

        if (projectile is Trident) {
            throwLocations[player.uniqueId] = player.location
        }
    }

    @EventHandler
    fun onTridentHit(event: ProjectileHitEvent) {
        val projectile = event.entity
        val shooter = projectile.shooter

        if (projectile is Trident && shooter is Player) {
            val throwLocation = throwLocations[shooter.uniqueId]

            if (throwLocation != null) {
                val hitLocation = event.hitBlock?.location ?: event.hitEntity?.location ?: projectile.location
                val distance = calculate2DDistance(throwLocation, hitLocation)

                updateLeaderboard(shooter.uniqueId, shooter.name, distance)

                val formattedDistance = String.format("%.2f", distance)
                val distanceUnit = Config.getUnitFromConfig()
                val message = Component.text("Trident thrown by ")
                    .append(Component.text(shooter.name, NamedTextColor.GOLD))
                    .append(Component.text(" traveled "))
                    .append(Component.text(formattedDistance, NamedTextColor.AQUA).decorate(TextDecoration.UNDERLINED))
                    .append(Component.text(" $distanceUnit"))
                Bukkit.getServer().onlinePlayers.forEach { it.sendMessage(message) }
                createTemporaryBeacon(hitLocation, shooter.name, distance, distance >= worldRecord)
            }
        }
    }

    private fun calculate2DDistance(loc1: Location, loc2: Location): Double {
        val dx = loc1.x - loc2.x
        val dz = loc1.z - loc2.z
        return sqrt(dx * dx + dz * dz)
    }

    private fun updateLeaderboard(uuid: UUID, name: String, distance: Double) {
        val entry = leaderboard[uuid]
        if (entry == null || distance > entry.distance) {
            leaderboard[uuid] = LeaderboardEntry(uuid, name, distance)
            saveLeaderboard()

            // Check for new world record
            if (distance > worldRecord) {
                worldRecord = distance
                announceNewWorldRecord(name, distance)
            }
        }
    }

    private fun announceNewWorldRecord(playerName: String, distance: Double) {
        val formattedDistance = String.format("%.2f", distance)
        val distanceUnit = Config.getUnitFromConfig()
        val message = Component.text("NEW WORLD RECORD! ")
            .color(NamedTextColor.GOLD)
            .decorate(TextDecoration.BOLD)
            .append(Component.text(playerName, NamedTextColor.GREEN))
            .append(Component.text(" has set a new javelin throw record of "))
            .append(Component.text(formattedDistance, NamedTextColor.AQUA).decorate(TextDecoration.UNDERLINED))
            .append(Component.text(" $distanceUnit!"))

        // Announce to all online players
        Bukkit.getServer().onlinePlayers.forEach { it.sendMessage(message) }

        // Log the new record
        logger.info("New world record set by $playerName: $formattedDistance $distanceUnit")
    }

    private fun loadLeaderboard() {
        if (leaderboardFile.length() > 0) {
            val jsonParser = JSONParser()
            val jsonArray = jsonParser.parse(leaderboardFile.reader()) as JSONArray

            jsonArray.forEach { item ->
                val jsonObject = item as JSONObject
                val uuid = UUID.fromString(jsonObject["uuid"] as String)
                val name = jsonObject["name"] as String
                val distance = (jsonObject["distance"] as Number).toDouble()
                leaderboard[uuid] = LeaderboardEntry(uuid, name, distance)

                // Update world record
                if (distance > worldRecord) {
                    worldRecord = distance
                }
            }
        }
    }

    private fun saveLeaderboard() {
        val jsonArray = JSONArray()
        leaderboard.values.forEach { entry ->
            val jsonObject = JSONObject()
            jsonObject["uuid"] = entry.uuid.toString()
            jsonObject["name"] = entry.name
            jsonObject["distance"] = entry.distance
            jsonArray.add(jsonObject)
        }

        leaderboardFile.writeText(jsonArray.toJSONString())
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (command.name.equals("javelinlb", ignoreCase = true)) {
            if (sender is Player) {
                displayLeaderboard(sender)
            } else {
                logger.info("This command can only be used by players.")
            }
            return true
        }
        return false
    }

    private fun displayLeaderboard(player: Player) {
        val sortedLeaderboard = leaderboard.values.sortedByDescending { it.distance }
        player.sendMessage(Component.text("===== Javelin Leaderboard =====").color(NamedTextColor.GOLD))
        sortedLeaderboard.take(10).forEachIndexed { index, entry ->
            player.sendMessage(
                Component.text("${index + 1}. ${entry.name}: ")
                    .append(Component.text(String.format("%.2f", entry.distance)).color(NamedTextColor.AQUA))
                    .append(Component.text(" ${Config.getUnitFromConfig()}"))
            )
        }
    }

    private fun createTemporaryBeacon(
        location: Location,
        throwerName: String,
        distance: Double,
        isWorldRecord: Boolean
    ) {
        object : BukkitRunnable() {
            var duration = 10 // Duration in seconds

            override fun run() {
                if (duration <= 0) {
                    this.cancel()
                    return
                }

                // Create the beacon beam effect
                val beaconColor = if (isWorldRecord) Color.YELLOW else Color.AQUA
                val dustOptions = Particle.DustOptions(beaconColor, 1.5f)

                // Create vertical beam by spawning particles from the ground up to 10 blocks
                for (y in 0..10) {
                    location.world.spawnParticle(
                        Particle.DUST,
                        location.clone().add(0.0, y.toDouble(), 0.0),
                        10,  // number of particles
                        0.2, 0.2, 0.2,  // spread in x, y, z directions
                        dustOptions
                    )
                }

                // Play a sound at the location
                location.world.playSound(location, Sound.BLOCK_BEACON_AMBIENT, 1.0f, 1.0f)

                // Optionally display some text/hologram near the beacon
                val recordText = if (isWorldRecord) " §6§lNEW WORLD RECORD!" else ""
                val infoText = "§e$throwerName §fthrew §b${
                    String.format("%.2f", distance)
                } §f${Config.getUnitFromConfig()}!$recordText"
                for (player in location.world.players) {
                    if (player.location.distance(location) <= 70) { // Only show to nearby players
                        player.sendActionBar(Component.text(infoText, NamedTextColor.YELLOW))
                    }
                }

                duration--
            }
        }.runTaskTimer(this, 0L, 20L) // Run every second
    }

    override fun onDisable() {
        saveLeaderboard()
        logger.info("Javelin has been disabled!")
    }
}