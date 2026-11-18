package org.desp.randomBox.crate;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * IDEQuest 퀘스트 조회 브릿지. EtCetera QuestCompat 과 같은 방식이다.
 * IDEQuest 클래스는 내부 클래스 안에서만 참조하므로 IDEQuest 가 없는 서버에서도 문제없이 동작한다.
 */
public final class QuestCompat {

    private QuestCompat() {
    }

    public static boolean hasQuestData(Player player, int questId) {
        if (!Bukkit.getPluginManager().isPluginEnabled("IDEQuest")) {
            return false;
        }
        try {
            return Ide.loaded() && Ide.hasQuestData(player, questId);
        } catch (Throwable e) {
            return false;
        }
    }

    private static final class Ide {
        static boolean loaded() {
            return org.example.dople.iDEQuest.api.IDEQuestAPI.isLoaded();
        }

        static boolean hasQuestData(Player player, int questId) {
            return org.example.dople.iDEQuest.api.IDEQuestAPI.hasQuestData(player.getUniqueId(), questId);
        }
    }
}
