package org.screamingsandals.bedwars.game;

import lombok.Getter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.screamingsandals.bedwars.api.Team;

import static org.screamingsandals.bedwars.lib.lang.I.i18nonly;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.bukkit.Location;
import org.bukkit.entity.Item;
import org.screamingsandals.bedwars.api.config.ConfigurationContainer;
import org.screamingsandals.bedwars.config.MainConfig;
import org.screamingsandals.lib.hologram.Hologram;
import org.screamingsandals.lib.hologram.HologramManager;
import org.screamingsandals.lib.material.builder.ItemFactory;
import org.screamingsandals.lib.player.PlayerMapper;
import org.screamingsandals.lib.tasker.TaskerTime;
import org.screamingsandals.lib.utils.AdventureHelper;
import org.screamingsandals.lib.utils.Pair;
import org.screamingsandals.lib.world.LocationMapper;

public class ItemSpawner implements org.screamingsandals.bedwars.api.game.ItemSpawner {
    public Location loc;
    public ItemSpawnerType type;
    public String customName;
    public double startLevel;
    public double currentLevel;
    public int maxSpawnedResources;
    public boolean hologramEnabled;
    public boolean floatingEnabled;
    public Team team;
    public List<Item> spawnedItems;
    public boolean spawnerIsFullHologram = false;
    public boolean rerenderHologram = false;
    public double currentLevelOnHologram = -1;
    public org.screamingsandals.lib.hologram.Hologram.RotationMode rotationMode;
    @Getter
    private org.screamingsandals.lib.hologram.Hologram hologram;
    public final static String ARMOR_STAND_DISPLAY_NAME_HIDDEN = "BEDWARS_FLOATING_ROT_ENTITY";

    public ItemSpawner(Location loc, ItemSpawnerType type, String customName,
                       boolean hologramEnabled, double startLevel, Team team,
                       int maxSpawnedResources, boolean floatingEnabled, org.screamingsandals.lib.hologram.Hologram.RotationMode rotationMode) {
        this.loc = loc;
        this.type = type;
        this.customName = customName;
        this.currentLevel = this.startLevel = startLevel;
        this.hologramEnabled = hologramEnabled;
        this.team = team;
        this.spawnedItems = new ArrayList<>();
        this.maxSpawnedResources = maxSpawnedResources;
        this.floatingEnabled = floatingEnabled;
        this.rotationMode = rotationMode;
    }

    @Override
    public org.screamingsandals.bedwars.api.game.ItemSpawnerType getItemSpawnerType() {
        return type;
    }

    @Override
    public Location getLocation() {
        return loc;
    }

    @Override
    public boolean hasCustomName() {
        return customName != null;
    }

    @Override
    public String getCustomName() {
        return customName;
    }

    @Override
    public double getStartLevel() {
        return startLevel;
    }

    @Override
    public double getCurrentLevel() {
        return currentLevel;
    }

    @Override
    public boolean getHologramEnabled() {
        return hologramEnabled;
    }

    @Override
    public boolean getFloatingEnabled() {
        return floatingEnabled;
    }

    @Override
    public void setCurrentLevel(double level) {
        currentLevel = level;
    }

    @Override
    public Optional<Team> getTeam() {
        return Optional.ofNullable(team);
    }

    @Override
    public void setTeam(Team team) {
        this.team = team;
    }

    public int getMaxSpawnedResources() {
        return maxSpawnedResources;
    }

    public int nextMaxSpawn(int calculated) {
        if (currentLevel <= 0) {
            if (hologram != null && (!spawnerIsFullHologram || currentLevelOnHologram != currentLevel)) {
                spawnerIsFullHologram = true;
                currentLevelOnHologram = currentLevel;
                hologram.setLine(1, AdventureHelper.toComponent(i18nonly("spawner_not_enough_level").replace("%levels%", String.valueOf((currentLevelOnHologram * (-1)) + 1))));
            }
            return 0;
        }

        if (maxSpawnedResources <= 0) {
            if (spawnerIsFullHologram && !rerenderHologram) {
                spawnerIsFullHologram = false;
                rerenderHologram = true;
            }
            return calculated;
        }

        /* Update spawned items */
        spawnedItems.removeIf(Entity::isDead);

        int spawned = spawnedItems.size();

        if (spawned >= maxSpawnedResources) {
            if (hologram != null && !spawnerIsFullHologram) {
                spawnerIsFullHologram = true;
                hologram.setLine(1, AdventureHelper.toComponent(i18nonly("spawner_is_full")));
            }
            return 0;
        }

        if ((maxSpawnedResources - spawned) >= calculated) {
            if (spawnerIsFullHologram && !rerenderHologram) {
                rerenderHologram = true;
                spawnerIsFullHologram = false;
            } else if (hologram != null && (calculated + spawned) == maxSpawnedResources) {
                spawnerIsFullHologram = true;
                hologram.setLine(1, AdventureHelper.toComponent(i18nonly("spawner_is_full")));
            }
            return calculated;
        }

        if (hologram != null && !spawnerIsFullHologram) {
            spawnerIsFullHologram = true;
            hologram.setLine(1, AdventureHelper.toComponent(i18nonly("spawner_is_full")));
        }

        return maxSpawnedResources - spawned;
    }

    public void add(Item item) {
        if (maxSpawnedResources > 0 && !spawnedItems.contains(item)) {
            spawnedItems.add(item);
        }
    }

    public void remove(Item item) {
        if (maxSpawnedResources > 0 && spawnedItems.contains(item)) {
            spawnedItems.remove(item);
            if (spawnerIsFullHologram && maxSpawnedResources > spawnedItems.size()) {
                spawnerIsFullHologram = false;
                rerenderHologram = true;
            }
        }
    }

    public void spawnHologram(List<Player> viewers, boolean countdownHologram) {
        try {
            Location loc;
            if (floatingEnabled &&
                    MainConfig.getInstance().node("floating-generator", "enabled").getBoolean(true)) {
                loc = this.loc.clone().add(0, MainConfig.getInstance().node("floating-generator", "holo-height").getDouble(0.5), 0);
            } else {
                loc = this.loc.clone().add(0,
                        MainConfig.getInstance().node("spawner-holo-height").getDouble(0.25), 0);
            }
            hologram = HologramManager
                    .hologram(LocationMapper.wrapLocation(loc))
                    .firstLine(AdventureHelper.toComponent(type.getItemBoldName()));

            if (countdownHologram) {
                hologram.newLine(
                        AdventureHelper.toComponent(
                                type.getInterval() < 2 ? i18nonly("every_second_spawning")
                                        : i18nonly("countdown_spawning").replace("%seconds%",
                                        Integer.toString(type.getInterval()))
                        )
                );
            }

            if (floatingEnabled &&
                    MainConfig.getInstance().node("floating-generator", "enabled").getBoolean(true)) {
                hologram
                        .item(
                                ItemFactory
                                        .build(type.getMaterial().name().substring(0, type.getMaterial().name().indexOf("_")) + "_BLOCK")
                                        .or(() -> ItemFactory.build(type.getMaterial().name()))
                                        .orElseThrow()
                        )
                        .itemLocation(Hologram.ItemLocation.BELOW)
                        .rotationMode(rotationMode)
                        .rotationTime(Pair.of(2, TaskerTime.TICKS));
            }

            hologram.show();

            viewers.stream()
                    .map(PlayerMapper::wrapPlayer)
                    .forEach(hologram::addViewer);

        } catch (Throwable t) {
            t.printStackTrace();
            destroy();
        }
    }

    public void destroy() {
        if (hologram != null) {
            hologram.destroy();
            hologram = null;
        }
    }
}
