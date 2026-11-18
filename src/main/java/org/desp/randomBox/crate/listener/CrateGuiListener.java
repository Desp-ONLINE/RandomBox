package org.desp.randomBox.crate.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;
import org.desp.randomBox.crate.gui.CrateMilestoneGui;
import org.desp.randomBox.crate.gui.CratePreviewGui;
import org.desp.randomBox.crate.opening.OpeningSession;

/** 미리보기, 마일스톤, 오픈 애니메이션 화면 클릭 처리. 아이템은 꺼낼 수 없다. */
public class CrateGuiListener implements Listener {

    private static boolean isCrateGui(InventoryHolder holder) {
        return holder instanceof CratePreviewGui || holder instanceof CrateMilestoneGui || holder instanceof OpeningSession;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (!isCrateGui(holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        int slot = event.getRawSlot();

        if (holder instanceof CratePreviewGui preview) {
            if (slot == preview.getExitSlot()) {
                player.closeInventory();
            } else if (slot == preview.getMilestoneSlot() && preview.getCrate().hasMilestones()) {
                CrateMilestoneGui.open(player, preview.getCrate(), 0);
            } else if (slot == preview.getNextSlot() && preview.getPage() + 1 < preview.getMaxPage()) {
                CratePreviewGui.open(player, preview.getCrate(), preview.getPage() + 1);
            } else if (slot == preview.getPreviousSlot() && preview.getPage() > 0) {
                CratePreviewGui.open(player, preview.getCrate(), preview.getPage() - 1);
            }
            return;
        }

        if (holder instanceof CrateMilestoneGui milestone) {
            if (slot == milestone.getReturnSlot()) {
                CratePreviewGui.open(player, milestone.getCrate(), 0);
            } else if (slot == milestone.getNextSlot() && milestone.getPage() + 1 < milestone.getMaxPage()) {
                CrateMilestoneGui.open(player, milestone.getCrate(), milestone.getPage() + 1);
            } else if (slot == milestone.getPreviousSlot() && milestone.getPage() > 0) {
                CrateMilestoneGui.open(player, milestone.getCrate(), milestone.getPage() - 1);
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (isCrateGui(event.getView().getTopInventory().getHolder())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        // 애니메이션 도중 창을 닫아도 보상은 바로 지급한다.
        if (event.getInventory().getHolder() instanceof OpeningSession session) {
            session.finish(false);
        }
    }
}
