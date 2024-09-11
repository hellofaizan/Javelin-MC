package me.hellofaizan.javelin

import com.destroystokyo.paper.event.player.PlayerLaunchProjectileEvent
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.Trident
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.ProjectileHitEvent
import org.bukkit.plugin.java.JavaPlugin

class Javelin : JavaPlugin(), Listener {

    private var referencePoint: Location? = null
    private val throwLocations = mutableMapOf<String, Location>()

    override fun onEnable() {
        // Plugin startup logic
        // Load the world and set the reference point
        val world: World? = Bukkit.getWorld("world")
        if (world != null) {
            // Set the reference point once the world is loaded
            referencePoint = Location(world, 100.0, 64.0, 100.0)
        } else {
            logger.warning("The specified world could not be found. Make sure 'world' is loaded.")
        }
        saveDefaultConfig()
        Config.init()
        // Register the event listener
        server.pluginManager.registerEvents(this, this)
        logger.info("Javelin has been enabled!")
    }

    @EventHandler
    fun onTridentThrow(event: PlayerLaunchProjectileEvent) {
        val projectile = event.projectile
        val player = event.player

        // Check if the projectile is a trident
        if (projectile is Trident) {
            // Get the location of the player (where the trident was thrown from)
            val throwLocation = player.location
            throwLocations[player.name] = throwLocation
        }
    }

    // on trident hit
    @EventHandler
    fun onTridentHit(event: ProjectileHitEvent) {
        val projectile = event.entity
        val shooter = projectile.shooter

        if (projectile is Trident && shooter is org.bukkit.entity.Player) {
            val throwLocation = throwLocations[shooter.name]

            if (throwLocation != null) {
                // Get the hit location (block or entity hit by the trident)
                val hitLocation = if (event.hitBlock != null) {
                    event.hitBlock!!.location // Prioritize block hit
                } else {
                    event.hitEntity?.location ?: projectile.location // Use entity hit if no block hit, otherwise final resting location
                }

                // Calculate the distance between the throw location and hit location
                val distance = calculate2DDistance(throwLocation, hitLocation)

                // Send a message to the player with the distance
                val formattedDistance = String.format("%.2f", distance)
                val distanceUnit = Config.getUnitFromConfig()
                val message = Component.text("Trident thrown by ")
                    .append(Component.text(shooter.name, NamedTextColor.GOLD))
                    .append(Component.text(" traveled "))
                    .append(
                        Component.text(formattedDistance, NamedTextColor.AQUA)
                            .decorate(TextDecoration.UNDERLINED)
                    )
                    .append(Component.text(" $distanceUnit"))
                for (onlinePlayer in Bukkit.getServer().onlinePlayers) {
                    onlinePlayer.sendMessage(message)
                }
            }
        }
    }

    private fun calculate2DDistance(loc1: Location, loc2: Location): Double {
        val dx = loc1.x - loc2.x
        val dz = loc1.z - loc2.z
        return Math.sqrt(dx * dx + dz * dz) // Pythagorean theorem (ignoring Y axis)
    }

    override fun onDisable() {
        // Plugin shutdown logic
        logger.info("Javelin has been disabled!")
    }
}
