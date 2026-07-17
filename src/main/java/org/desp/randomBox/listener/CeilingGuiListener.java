package org.desp.randomBox.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.desp.randomBox.gui.CeilingGui;

// 천장 현황 GUI 는 읽기 전용이므로 클릭/드래그를 모두 막아 아이템을 꺼내지 못하게 한다.
public class CeilingGuiListener implements Listener {

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof CeilingGui) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof CeilingGui) {
            event.setCancelled(true);
        }
    }
}
