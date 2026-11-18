package org.desp.randomBox.crate.listener;

import net.Indyuce.mmoitems.MMOItems;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.desp.randomBox.RandomBox;
import org.desp.randomBox.crate.Crate;
import org.desp.randomBox.crate.CrateHologramManager;
import org.desp.randomBox.crate.CrateManager;
import org.desp.randomBox.crate.CrateOpenService;
import org.desp.randomBox.crate.CrateReward;
import org.desp.randomBox.crate.CrateText;
import org.desp.randomBox.crate.CrateUserRepository;
import org.desp.randomBox.crate.QuestCompat;
import org.desp.randomBox.crate.gui.CratePreviewGui;
import org.desp.randomBox.crate.opening.OpeningSession;
import org.dople.dataSync.inventory.InventorySyncListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 설치된 상자 블록 처리. 좌클릭은 미리보기, 우클릭은 열쇠를 들고 열기다.
 * 기존 EtCetera CrateListener 의 조건(데이터 로드, 퀘스트, 1초 간격, 열쇠 소모)을 그대로 옮겼다.
 */
public class CrateBlockListener implements Listener {

    private final Set<UUID> openCooldown = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> previewCooldown = new ConcurrentHashMap<>();

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.LEFT_CLICK_BLOCK && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }
        CrateManager manager = CrateManager.getInstance();
        Crate crate = manager.getCrateAt(block);
        if (crate == null || !manager.isBlockCrateEnabled()) {
            return;
        }
        if (CrateManager.isExcellentCratesActive()) {
            // 이전 작업 동안은 EC가 같은 블록을 처리하므로 손대지 않는다.
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();

        if (action == Action.LEFT_CLICK_BLOCK) {
            openPreview(player, crate);
            return;
        }
        openCrate(player, crate, block.getLocation());
    }

    private void openPreview(Player player, Crate crate) {
        long cooldown = CrateManager.getInstance().getConfig().getLong("preview-cooldown-ms", 2500L);
        long now = System.currentTimeMillis();
        Long last = previewCooldown.get(player.getUniqueId());
        if (last != null && now - last < cooldown) {
            double left = (cooldown - (now - last)) / 1000.0;
            player.sendMessage(CrateText.mm("<lgray><lred><time>초</lred> 뒤에 확인할 수 있습니다.</lgray>",
                    CrateText.unparsed("time", String.format("%.1f", left))));
            return;
        }
        previewCooldown.put(player.getUniqueId(), now);
        CratePreviewGui.open(player, crate, 0);
    }

    private void openCrate(Player player, Crate crate, Location blockLocation) {
        if (InventorySyncListener.isDataLoading(player) || !CrateUserRepository.getInstance().isLoaded(player)) {
            player.sendMessage("§c 데이터 로드중입니다.");
            return;
        }
        if (OpeningSession.isOpening(player)) {
            return;
        }
        if (crate.getRequiredQuest() > 0 && !QuestCompat.hasQuestData(player, crate.getRequiredQuest())) {
            player.sendMessage("§c  " + (crate.getRequiredQuest() - 1) + "번 메인 퀘스트를 먼저 클리어하세요!");
            return;
        }
        if (openCooldown.contains(player.getUniqueId())) {
            player.sendMessage("§c  1초 간격으로 오픈할 수 있습니다.");
            return;
        }

        ItemStack hand = player.getInventory().getItemInMainHand();
        if (crate.requiresKey()) {
            String handId = hand.getType() == Material.AIR ? null : MMOItems.getID(hand);
            if (!Objects.equals(handId, crate.getKeyItemId())) {
                String keyName = crate.getKeyName() == null || crate.getKeyName().isEmpty() ? "열쇠" : crate.getKeyName();
                player.sendMessage("§c  " + CrateText.withObjectParticle(keyName) + " 손에 들고 시도하세요.");
                return;
            }
        }

        int count = massOpenCount(player, crate, hand);
        if (count > 1) {
            List<CrateReward> rewards = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                CrateReward reward = CrateOpenService.roll(player, crate, crate.getId());
                if (reward == null) {
                    break;
                }
                rewards.add(reward);
            }
            if (rewards.isEmpty()) {
                player.sendMessage("§c 이 상자에는 받을 수 있는 보상이 없습니다. 관리자에게 문의해 주세요.");
                return;
            }
            hand.setAmount(hand.getAmount() - rewards.size());
            startCooldown(player);
            CrateOpenService.progressMilestones(player, crate, rewards.size());
            CrateOpenService.deliverBlockRewards(player, crate, rewards, blockLocation);
            return;
        }

        CrateReward reward = CrateOpenService.roll(player, crate, crate.getId());
        if (reward == null) {
            player.sendMessage("§c 이 상자에는 받을 수 있는 보상이 없습니다. 관리자에게 문의해 주세요.");
            return;
        }

        if (crate.requiresKey()) {
            hand.setAmount(hand.getAmount() - 1);
        }
        startCooldown(player);

        CrateOpenService.progressMilestones(player, crate);
        CrateOpenService.present(player, crate, reward,
                () -> CrateOpenService.deliverBlockReward(player, crate, reward, blockLocation));
    }

    /**
     * 쉬프트 우클릭으로 한번에 열 개수. 열쇠가 필요한 상자는 손에 든 열쇠 개수만큼(mass-open-limit 까지) 연다.
     * 쉬프트를 누르지 않았거나 열쇠가 필요 없는 상자면 1을 돌려준다.
     */
    private int massOpenCount(Player player, Crate crate, ItemStack hand) {
        if (!player.isSneaking() || !crate.requiresKey()) {
            return 1;
        }
        int limit = Math.max(1, CrateManager.getInstance().getConfig().getInt("mass-open-limit", 64));
        return Math.min(hand.getAmount(), limit);
    }

    private void startCooldown(Player player) {
        UUID uuid = player.getUniqueId();
        openCooldown.add(uuid);
        long interval = CrateManager.getInstance().getConfig().getLong("open-interval-ticks", 10L);
        Bukkit.getScheduler().runTaskLater(RandomBox.getInstance(), () -> openCooldown.remove(uuid), interval);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (CrateManager.getInstance().getCrateAt(event.getBlock()) != null && CrateManager.getInstance().isBlockCrateEnabled()) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        // 엔티티가 붙기 전에 만들면 중복될 수 있으므로 한 틱 뒤에 만든다.
        Bukkit.getScheduler().runTask(RandomBox.getInstance(), () -> {
            if (event.getChunk().isLoaded()) {
                CrateHologramManager.getInstance().onChunkLoad(event.getChunk());
            }
        });
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        CrateHologramManager.getInstance().onChunkUnload(event.getChunk());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        CrateUserRepository.getInstance().loadAsync(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        OpeningSession session = OpeningSession.get(player);
        if (session != null) {
            session.finish(false);
        }
        CrateUserRepository.getInstance().unload(player);
        openCooldown.remove(player.getUniqueId());
        previewCooldown.remove(player.getUniqueId());
    }
}
