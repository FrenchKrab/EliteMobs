package com.magmaguy.elitemobs.api;

import com.magmaguy.elitemobs.EliteMobs;
import com.magmaguy.elitemobs.MetadataHandler;
import com.magmaguy.elitemobs.adventurersguild.GuildRank;
import com.magmaguy.elitemobs.combatsystem.CombatSystem;
import com.magmaguy.elitemobs.config.MobCombatSettingsConfig;
import com.magmaguy.elitemobs.entitytracker.EntityTracker;
import com.magmaguy.elitemobs.mobconstructor.EliteEntity;
import com.magmaguy.elitemobs.mobconstructor.custombosses.CustomBossEntity;
import com.magmaguy.elitemobs.playerdata.ElitePlayerInventory;
import com.magmaguy.elitemobs.thirdparty.worldguard.WorldGuardCompatibility;
import com.magmaguy.elitemobs.thirdparty.worldguard.WorldGuardFlagChecker;
import com.magmaguy.elitemobs.utils.EntityFinder;
import com.magmaguy.elitemobs.utils.EventCaller;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.concurrent.ThreadLocalRandom;

public class EliteMobDamagedByPlayerEvent extends Event implements Cancellable {

    private static final HandlerList handlers = new HandlerList();
    private final Entity entity;
    private final EliteEntity eliteEntity;
    private final Player player;
    private boolean isCancelled = false;
    public boolean rangedAttack;
    private final EntityDamageByEntityEvent entityDamageByEntityEvent;
    private final double damage;
    private final boolean criticalStrike;
    private final boolean isCustomDamage;

    public EliteMobDamagedByPlayerEvent(EliteEntity eliteEntity,
                                        Player player,
                                        EntityDamageByEntityEvent event,
                                        double damage,
                                        boolean criticalStrike,
                                        boolean isCustomDamage) {
        this.damage = damage;
        this.entity = eliteEntity.getLivingEntity();
        this.eliteEntity = eliteEntity;
        this.player = player;
        this.entityDamageByEntityEvent = event;
        this.rangedAttack = event.getDamager() instanceof Projectile;
        this.criticalStrike = criticalStrike;
        this.isCustomDamage = isCustomDamage;
    }

    public Entity getEntity() {
        return entity;
    }

    public EliteEntity getEliteMobEntity() {
        return eliteEntity;
    }

    public Player getPlayer() {
        return player;
    }

    public EntityDamageByEntityEvent getEntityDamageByEntityEvent() {
        return entityDamageByEntityEvent;
    }

    public double getDamage() {
        return damage;
    }

    public boolean isCriticalStrike() {
        return criticalStrike;
    }

    public boolean isCustomDamage() {
        return isCustomDamage;
    }

    @Override
    public boolean isCancelled() {
        return this.isCancelled;
    }

    @Override
    public void setCancelled(boolean b) {
        this.isCancelled = b;
        entityDamageByEntityEvent.setCancelled(b);
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }

    public static class EliteMobDamagedByPlayerEventFilter implements Listener {
        @EventHandler(ignoreCancelled = true)
        public void onEliteMobAttack(EntityDamageByEntityEvent event) {
            LivingEntity livingEntity = EntityFinder.filterRangedDamagers(event.getDamager());
            if (livingEntity == null) return;
            if (!livingEntity.getType().equals(EntityType.PLAYER)) return;
            Player player = (Player) livingEntity;
            EliteEntity eliteEntity = EntityTracker.getEliteMobEntity(event.getEntity().getUniqueId());
            if (eliteEntity == null) return;
            //If the damage wasn't caused by an elite item, just allow the event to go as raw
            EliteMobDamagedByPlayerEvent eliteMobDamagedByPlayerEvent;
            double damage;
            //Thorns overrides all other possible damage
            if (event.getCause().equals(EntityDamageEvent.DamageCause.THORNS)) {
                int thornsLevel = ElitePlayerInventory.playerInventories.get(player.getUniqueId()).getThornsLevel();
                Strike strike = new Strike(thornsLevel, false, true, 1);
                eliteMobDamagedByPlayerEvent = new EliteMobDamagedByPlayerEvent(eliteEntity,
                        player,
                        event,
                        strike.damage,
                        strike.criticalStrike,
                        strike.customDamage);
                damage = strike.damage;
            } else {
                //Runs if the damage was dealt by an elite item
                if (EliteMobsItemDetector.isEliteMobsItem(player.getInventory().getItemInMainHand())) {
                    Strike strike = getDamage(player, eliteEntity, event);
                    eliteMobDamagedByPlayerEvent = new EliteMobDamagedByPlayerEvent(eliteEntity,
                            player,
                            event,
                            strike.damage,
                            strike.criticalStrike,
                            strike.customDamage);
                    damage = strike.damage;
                } else {
                    //Runs if the damage was not dealt by an elite item, important for other plugins
                    eliteMobDamagedByPlayerEvent = new EliteMobDamagedByPlayerEvent(eliteEntity,
                            player,
                            event,
                            event.getFinalDamage(),
                            false,
                            false);
                    damage = event.getFinalDamage();
                }
            }

            new EventCaller(eliteMobDamagedByPlayerEvent);
            if (eliteMobDamagedByPlayerEvent.isCancelled) {
                event.setCancelled(true);
                return;
            }
            execute(eliteMobDamagedByPlayerEvent);

            //nullify vanilla reductions
            for (EntityDamageEvent.DamageModifier modifier : EntityDamageEvent.DamageModifier.values())
                if (event.isApplicable(modifier))
                    event.setDamage(modifier, 0);

            event.setDamage(damage);

            //No antiexploit checks for dungeons
            if (!(EliteMobs.worldGuardIsEnabled &&
                    !WorldGuardFlagChecker.checkFlag(
                            eliteEntity.getLivingEntity().getLocation(),
                            WorldGuardCompatibility.getEliteMobsAntiExploitFlag())))
                if (!eliteEntity.isInAntiExploitCooldown())
                    Bukkit.getServer().getPluginManager().callEvent(
                            new EliteMobDamagedByPlayerAntiExploitEvent(eliteEntity, eliteMobDamagedByPlayerEvent));
        }

        public static void execute(EliteMobDamagedByPlayerEvent event) {
            event.getEliteMobEntity().addDamager(event.getPlayer(), event.getDamage());
        }

        private class Strike {
            public double damage;
            public boolean criticalStrike;
            public boolean customDamage;
            public double damageModifier;

            public Strike(double damage, boolean criticalStrike, boolean customDamage, double damageModifier) {
                this.damage = damage;
                this.criticalStrike = criticalStrike;
                this.customDamage = customDamage;
                this.damageModifier = damageModifier;
            }
        }

        public Strike getDamage(Player player, EliteEntity eliteEntity, EntityDamageByEntityEvent event) {
            //citizens
            if (player.hasMetadata("NPC")) {
                return new Strike(DamageEliteMob.getDamageValue(eliteEntity, DamageEliteMob.DamageAmount.LOW), false, true, 1);
            }

            //if the damage source is custom , the damage is final
            if (CombatSystem.bypass) {
                double rawDamage = event.getDamage();
                CombatSystem.bypass = false;
                return new Strike(rawDamage, false, true, 1);
            }

            double playerWeaponTier;
            //Melee attacks dealt with ranged weapons should have a tier of 0
            if (!(event.getDamager() instanceof Arrow) &&
                    (player.getInventory().getItemInMainHand().getType().equals(Material.BOW) ||
                            player.getInventory().getItemInMainHand().getType().equals(Material.CROSSBOW)))
                playerWeaponTier = 0;
            else
                playerWeaponTier = ElitePlayerInventory.playerInventories.get(player.getUniqueId()).getWeaponTier(true);

            double damageModifier = 1;
            if (eliteEntity instanceof CustomBossEntity)
                damageModifier = ((CustomBossEntity) eliteEntity).getDamageModifier(player.getInventory().getItemInMainHand().getType());

            double newDamage = finalDamageCalculator(playerWeaponTier, player, eliteEntity, event.getDamager() instanceof Arrow, damageModifier);

            if (event.getDamager() instanceof Arrow) {
                //note: the arrow velocity amplitude at full load is about 2.8
                double arrowSpeedMultiplier = Math.sqrt(Math.pow(event.getDamager().getVelocity().getX(), 2D) +
                        Math.pow(event.getDamager().getVelocity().getY(), 2D) +
                        Math.pow(event.getDamager().getVelocity().getZ(), 2D));
                arrowSpeedMultiplier = Math.min(arrowSpeedMultiplier, 2.8D);
                arrowSpeedMultiplier = arrowSpeedMultiplier / 2.8D;
                newDamage *= arrowSpeedMultiplier;
            }

            boolean criticalHit = isCriticalHit(player);

            if (criticalHit) newDamage += newDamage * 0.5;

            return new Strike(newDamage, criticalHit, false, damageModifier);
        }

        /**
         * Calculates Player -> EliteMobs damage.
         *
         * @param player Player object
         * @return
         */
        private static double finalDamageCalculator(double playerWeaponTier, Player player, EliteEntity eliteEntity, boolean ranged, double damageModifier) {
            double finalDamage;
            if (!ranged)
                finalDamage = getCooledAttackStrength(player) *
                        (playerWeaponTier + secondaryEnchantmentDamageIncrease(player, eliteEntity.getLivingEntity())) *
                        MobCombatSettingsConfig.damageToEliteMultiplier;
            else
                finalDamage = (playerWeaponTier + secondaryEnchantmentDamageIncrease(player, eliteEntity.getLivingEntity())) *
                        MobCombatSettingsConfig.damageToEliteMultiplier;

            finalDamage *= damageModifier;

            return Math.max(finalDamage, 1D);
        }

        /**
         * Calculates the cooldown debuff following vanilla Minecraft rules and then linearly applies a debuff to attacks
         *
         * @param player Player in cooldown
         * @return Debuff multiplier
         */
        private static float getCooledAttackStrength(Player player) {
            if (!playerHitCooldownHashMap.containsKey(player)) return 1;
            float swingDelay = clock - playerHitCooldownHashMap.get(player);
            float cooldownPeriod = getCooldownPeriod(player);
            if (swingDelay > cooldownPeriod) return 1;
            return swingDelay / cooldownPeriod;
        }

        /**
         * Calculates the cooldown following vanilla rules
         *
         * @param player
         * @return
         */
        private static float getCooldownPeriod(Player player) {
            return (float) (1.0D / player.getAttribute(Attribute.GENERIC_ATTACK_SPEED).getValue() * 20.0D);
        }

        private static int clock = 0;

        public static void launchInternalClock() {
            new BukkitRunnable() {

                @Override
                public void run() {
                    if (clock == Integer.MAX_VALUE) clock = 0;
                    clock++;
                }

            }.runTaskTimer(MetadataHandler.PLUGIN, 1, 1);
        }

        private static double secondaryEnchantmentDamageIncrease(Player player, LivingEntity eliteMob) {

            if (eliteMob instanceof Spider || eliteMob instanceof Silverfish)
                return ElitePlayerInventory.playerInventories.get(player.getUniqueId()).mainhand.getDamageArthropodsLevel(player.getInventory().getItemInMainHand(), false);
            if (eliteMob instanceof Zombie || eliteMob instanceof Skeleton)
                return ElitePlayerInventory.playerInventories.get(player.getUniqueId()).mainhand.getDamageUndeadLevel(player.getInventory().getItemInMainHand(), false);

            return 0;

        }

        private static boolean isCriticalHit(Player player) {
            double criticalStrike = ElitePlayerInventory.playerInventories.get(player.getUniqueId()).getCritChance(false);
            criticalStrike += (GuildRank.critBonusValue(GuildRank.getGuildPrestigeRank(player), GuildRank.getActiveGuildRank(player)) / 100);
            return ThreadLocalRandom.current().nextDouble() < criticalStrike;
        }

        private static final HashMap<Player, Integer> playerHitCooldownHashMap = new HashMap<>();

        @EventHandler(priority = EventPriority.MONITOR)
        public void onPlayerInteract(PlayerInteractEvent event) {
            if (!(event.getAction().equals(Action.LEFT_CLICK_AIR) || event.getAction().equals(Action.LEFT_CLICK_BLOCK) ||
                    event.getAction().equals(Action.PHYSICAL))) return;
            playerHitCooldownHashMap.put(event.getPlayer(), clock);

        }

    }

}
