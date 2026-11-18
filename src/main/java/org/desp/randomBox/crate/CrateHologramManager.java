package org.desp.randomBox.crate;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.desp.randomBox.RandomBox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 상자 블록 위 홀로그램, 보상 표시, 블록 주변 파티클을 담당한다.
 * 홀로그램은 저장되지 않는(non-persistent) 디스플레이 엔티티라 청크가 내려가면 사라지고, 청크가 올라오면 다시 만든다.
 */
public class CrateHologramManager {

    private static CrateHologramManager instance;

    private final NamespacedKey tagKey = new NamespacedKey(RandomBox.getInstance(), "crate_display");
    /** 위치 키 -> 홀로그램 */
    private final Map<String, TextDisplay> holograms = new HashMap<>();
    /** 위치 키 -> 보상 표시 엔티티 */
    private final Map<String, List<Entity>> rewardDisplays = new HashMap<>();
    private final Map<String, BukkitTask> rewardTasks = new HashMap<>();
    private BukkitTask effectTask;
    private long step;

    public static CrateHologramManager getInstance() {
        if (instance == null) {
            instance = new CrateHologramManager();
        }
        return instance;
    }

    private static String key(Location location) {
        return location.getWorld().getName() + ":" + location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ();
    }

    // ------------------------------------------------------------ 전체 갱신

    /** 모든 홀로그램을 지우고 다시 만든다. 파티클 작업도 다시 시작한다. */
    public void refreshAll() {
        removeAll();
        removeStrayEntities();
        if (!CrateManager.getInstance().isBlockCrateEnabled()) {
            return;
        }
        for (Crate crate : CrateManager.getInstance().getCrates()) {
            for (Location location : CrateManager.getInstance().loadedPositions(crate)) {
                if (location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
                    spawnHologram(crate, location);
                }
            }
        }
        effectTask = Bukkit.getScheduler().runTaskTimer(RandomBox.getInstance(), this::playEffects, 1L, 1L);
    }

    public void removeAll() {
        if (effectTask != null) {
            effectTask.cancel();
            effectTask = null;
        }
        for (TextDisplay display : holograms.values()) {
            display.remove();
        }
        holograms.clear();
        for (BukkitTask task : rewardTasks.values()) {
            task.cancel();
        }
        rewardTasks.clear();
        for (List<Entity> entities : rewardDisplays.values()) {
            entities.forEach(Entity::remove);
        }
        rewardDisplays.clear();
    }

    /** 비정상 종료로 남은 표시 엔티티를 정리한다. */
    private void removeStrayEntities() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntitiesByClass(Display.class)) {
                if (isOurs(entity)) {
                    entity.remove();
                }
            }
        }
    }

    public boolean isOurs(Entity entity) {
        return entity.getPersistentDataContainer().has(tagKey, PersistentDataType.BYTE);
    }

    // ------------------------------------------------------------ 청크

    public void onChunkLoad(Chunk chunk) {
        if (!CrateManager.getInstance().isBlockCrateEnabled()) {
            return;
        }
        for (Crate crate : CrateManager.getInstance().getCrates()) {
            for (Location location : CrateManager.getInstance().loadedPositions(crate)) {
                if (location.getWorld().equals(chunk.getWorld())
                        && (location.getBlockX() >> 4) == chunk.getX() && (location.getBlockZ() >> 4) == chunk.getZ()) {
                    spawnHologram(crate, location);
                }
            }
        }
    }

    public void onChunkUnload(Chunk chunk) {
        holograms.entrySet().removeIf(entry -> {
            Location location = entry.getValue().getLocation();
            boolean inChunk = location.getWorld().equals(chunk.getWorld())
                    && (location.getBlockX() >> 4) == chunk.getX() && (location.getBlockZ() >> 4) == chunk.getZ();
            if (inChunk) {
                entry.getValue().remove();
            }
            return inChunk;
        });
    }

    // ------------------------------------------------------------ 홀로그램

    private Location hologramBase(Location block, Crate crate) {
        return block.clone().add(0.5, 1.15 + crate.getHologramYOffset(), 0.5);
    }

    private void spawnHologram(Crate crate, Location block) {
        if (!crate.isHologramEnabled()) {
            return;
        }
        String key = key(block);
        TextDisplay old = holograms.remove(key);
        if (old != null) {
            old.remove();
        }
        List<String> template = CrateManager.getInstance().getHologramTemplates().get(crate.getHologramTemplate());
        if (template == null || template.isEmpty()) {
            return;
        }
        Component text = hologramText(crate, template);
        float viewRange = CrateManager.getInstance().visibilityDistance() / 64f;
        TextDisplay display = block.getWorld().spawn(hologramBase(block, crate), TextDisplay.class, entity -> {
            entity.setPersistent(false);
            entity.getPersistentDataContainer().set(tagKey, PersistentDataType.BYTE, (byte) 1);
            entity.setBillboard(Display.Billboard.CENTER);
            entity.setViewRange(viewRange);
            entity.setLineWidth(400);
            entity.text(text);
        });
        holograms.put(key, display);
    }

    private Component hologramText(Crate crate, List<String> template) {
        Component text = Component.empty();
        for (int i = 0; i < template.size(); i++) {
            String line = template.get(i).replace("%crate_name%", "<crate_name>");
            text = text.append(CrateText.mm(line, CrateText.parsed("crate_name", crate.getName())));
            if (i < template.size() - 1) {
                text = text.append(Component.newline());
            }
        }
        return text;
    }

    /** 상자를 연 뒤 상자 위에 보상을 잠깐 띄운다. 그동안 상자 홀로그램은 숨긴다. */
    public void showReward(Location block, Crate crate, CrateReward reward) {
        String key = key(block);
        clearRewardDisplay(key, null, null);

        TextDisplay hologram = holograms.remove(key);
        if (hologram != null) {
            hologram.remove();
        }

        Location center = block.clone().add(0.5, 1.0, 0.5);
        float viewRange = CrateManager.getInstance().visibilityDistance() / 64f;
        List<Entity> spawned = new ArrayList<>();
        spawned.add(block.getWorld().spawn(center.clone().add(0, 0.45, 0), ItemDisplay.class, entity -> {
            entity.setPersistent(false);
            entity.getPersistentDataContainer().set(tagKey, PersistentDataType.BYTE, (byte) 1);
            entity.setItemStack(reward.previewItem());
            entity.setBillboard(Display.Billboard.VERTICAL);
            entity.setViewRange(viewRange);
        }));
        spawned.add(block.getWorld().spawn(center.clone().add(0, 0.95, 0), TextDisplay.class, entity -> {
            entity.setPersistent(false);
            entity.getPersistentDataContainer().set(tagKey, PersistentDataType.BYTE, (byte) 1);
            entity.setBillboard(Display.Billboard.CENTER);
            entity.setViewRange(viewRange);
            entity.text(reward.displayName());
        }));
        rewardDisplays.put(key, spawned);

        long duration = CrateManager.getInstance().getConfig().getLong("reward-display-ticks", 60L);
        rewardTasks.put(key, Bukkit.getScheduler().runTaskLater(RandomBox.getInstance(), () -> {
            rewardTasks.remove(key);
            clearRewardDisplay(key, crate, block);
        }, duration));
    }

    /** 보상 표시를 지운다. crate 와 block 을 주면 숨겨 둔 상자 홀로그램을 다시 띄운다. */
    private void clearRewardDisplay(String key, Crate crate, Location block) {
        BukkitTask task = rewardTasks.remove(key);
        if (task != null) {
            task.cancel();
        }
        List<Entity> entities = rewardDisplays.remove(key);
        if (entities != null) {
            entities.forEach(Entity::remove);
        }
        if (crate != null && block != null && block.getWorld() != null
                && block.getWorld().isChunkLoaded(block.getBlockX() >> 4, block.getBlockZ() >> 4)
                && CrateManager.getInstance().getCrate(crate.getId()) != null) {
            spawnHologram(CrateManager.getInstance().getCrate(crate.getId()), block);
        }
    }

    // ------------------------------------------------------------ 파티클

    private void playEffects() {
        step++;
        int distance = CrateManager.getInstance().visibilityDistance();
        double distanceSquared = (double) distance * distance;
        for (Crate crate : CrateManager.getInstance().getCrates()) {
            String model = crate.getEffectModel() == null ? "NONE" : crate.getEffectModel().toUpperCase();
            if (model.equals("NONE")) {
                continue;
            }
            Particle particle = particleOf(crate.getEffectParticle());
            for (Location block : CrateManager.getInstance().loadedPositions(crate)) {
                if (!block.getWorld().isChunkLoaded(block.getBlockX() >> 4, block.getBlockZ() >> 4)) {
                    continue;
                }
                Location center = block.clone().add(0.5, 0, 0.5);
                List<Player> viewers = new ArrayList<>();
                for (Player player : block.getWorld().getPlayers()) {
                    if (player.getLocation().distanceSquared(center) <= distanceSquared) {
                        viewers.add(player);
                    }
                }
                if (viewers.isEmpty()) {
                    continue;
                }
                List<Location> points = effectPoints(model, center, step);
                for (Player viewer : viewers) {
                    for (Location point : points) {
                        try {
                            viewer.spawnParticle(particle, point, 1, 0, 0, 0, 0);
                        } catch (Exception ignored) {
                            // 추가 데이터가 필요한 파티클은 보이지 않는다.
                        }
                    }
                }
            }
        }
    }

    /** EC HELIX, BEACON 효과와 같은 궤적. 그 밖의 모델은 상자 위에 파티클 하나를 띄운다. */
    private static List<Location> effectPoints(String model, Location center, long step) {
        List<Location> points = new ArrayList<>();
        switch (model) {
            case "HELIX" -> {
                Location base = center.clone().add(0, 0.05, 0);
                double angle = 0.3141592653589793 * step;
                double height = (step * 0.1) % 2.5;
                points.add(pointOnCircle(base, angle, 0.75, height));
                points.add(pointOnCircle(base, angle - Math.PI, 0.75, height));
            }
            case "BEACON" -> {
                double angle = 0.8975979010256552 * step;
                for (int i = 0; i < 4; i++) {
                    points.add(pointOnCircle(center, angle + i * (Math.PI / 2), 0.55, (step % 25) * 0.1));
                }
            }
            default -> {
                if (step % 5 == 0) {
                    points.add(center.clone().add(0, 1.2, 0));
                }
            }
        }
        return points;
    }

    private static Location pointOnCircle(Location base, double angle, double radius, double height) {
        return base.clone().add(Math.cos(angle) * radius, height, Math.sin(angle) * radius);
    }

    private static Particle particleOf(String name) {
        if (name == null || name.isEmpty() || name.equalsIgnoreCase("null")) {
            return Particle.CLOUD;
        }
        try {
            return Particle.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Particle.CLOUD;
        }
    }
}
