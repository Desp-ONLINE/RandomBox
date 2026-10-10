package org.desp.randomBox.listener;

import com.binggre.mmomail.MMOMail;
import com.binggre.mmomail.objects.Mail;
import net.Indyuce.mmoitems.MMOItems;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.desp.randomBox.boxUtils.SpecialWeaponTicketMapper;
import org.dople.dataSync.inventory.InventorySyncListener;

import java.util.List;

/**
 * 특수무기 획득권을 우클릭하면 해당 특수무기(+0)를 메일로 지급한다.
 * 획득권 <-> 특수무기 매핑은 {@link SpecialWeaponTicketMapper} 를 따른다.
 */
public class SpecialWeaponTicketListener implements Listener {

    @EventHandler
    public void onUseTicket(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!event.getAction().isRightClick()) return;

        Player player = event.getPlayer();
        ItemStack itemInMainHand = player.getInventory().getItemInMainHand();
        if (itemInMainHand.getType() == Material.AIR) return;

        String ticketId = MMOItems.getID(itemInMainHand);
        if (!SpecialWeaponTicketMapper.isTicket(ticketId)) return;

        event.setCancelled(true);

        if (InventorySyncListener.isDataLoading(player)) {
            player.sendMessage("§c 데이터가 로드중입니다.");
            return;
        }

        // 소모 전에 지급할 무기를 먼저 확보한다.
        ItemStack weapon = SpecialWeaponTicketMapper.toWeapon(ticketId);
        if (weapon == null) {
            player.sendMessage("§c 지급할 특수무기 정보를 찾을 수 없습니다. 관리자에게 문의하세요. §7(" + ticketId + ")");
            return;
        }
        weapon.setAmount(1);

        if (itemInMainHand.getAmount() > 1) {
            itemInMainHand.setAmount(itemInMainHand.getAmount() - 1);
        } else {
            player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        }
        player.updateInventory();

        MMOMail mmoMail = MMOMail.getInstance();
        Mail mail = mmoMail.getMailAPI().createMail(
                "시스템",
                "특수무기 획득권 사용 보상입니다.",
                0,
                List.of(weapon)
        );
        mmoMail.getMailAPI().sendMail(player.getName(), mail);

        String weaponName = weapon.getItemMeta() != null && weapon.getItemMeta().hasDisplayName()
                ? weapon.getItemMeta().getDisplayName()
                : MMOItems.getID(weapon);

        player.playSound(player, "uisounds:purchase1", 1, 1);
        player.sendMessage("§f " + weaponName + " §f을(를) 메일로 지급했습니다! §7§o(/메일함 또는 /ㅁ)");
    }
}
