package org.desp.randomBox;

import com.binggre.velocitysocketclient.VelocityClient;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.desp.randomBox.command.CeilingCommand;
import org.desp.randomBox.command.CeilingCheckCommand;
import org.desp.randomBox.command.CeilingRestoreCommand;
import org.desp.randomBox.command.CrateAdminCommand;
import org.desp.randomBox.command.ForcedRewardCommand;
import org.desp.randomBox.crate.CrateHologramManager;
import org.desp.randomBox.crate.CrateManager;
import org.desp.randomBox.crate.CrateUserRepository;
import org.desp.randomBox.crate.listener.CrateBlockListener;
import org.desp.randomBox.crate.listener.CrateGuiListener;
import org.desp.randomBox.crate.opening.OpeningSession;
import org.desp.randomBox.database.PlayerCeilingRepository;
import org.desp.randomBox.listener.BoxConfirmListener;
import org.desp.randomBox.listener.CeilingGuiListener;
import org.desp.randomBox.listener.CrateBoxListener;
import org.desp.randomBox.listener.PlayerListener;
import org.desp.randomBox.listener.RandomBoxListener;
import org.desp.randomBox.listener.VelocityProxyListener;

public final class RandomBox extends JavaPlugin {

    @Getter
    private static RandomBox instance;
    @Override
    public void onEnable() {
        instance = this;

        VelocityClient.getInstance().getConnectClient().registerListener(VelocityProxyListener.class);

        // 자체 상자 시스템(ExcellentCrates 대체). 홀로그램은 월드가 모두 올라온 뒤에 만든다.
        CrateManager.getInstance().reload();
        Bukkit.getScheduler().runTask(this, () -> CrateHologramManager.getInstance().refreshAll());

        registerCommands();

        Bukkit.getPluginManager().registerEvents(new BoxConfirmListener(), this);
        Bukkit.getPluginManager().registerEvents(new RandomBoxListener(), this);
        Bukkit.getPluginManager().registerEvents(new PlayerListener(), this);
        Bukkit.getPluginManager().registerEvents(new CrateBoxListener(), this);
        Bukkit.getPluginManager().registerEvents(new CeilingGuiListener(), this);
        Bukkit.getPluginManager().registerEvents(new CrateBlockListener(), this);
        Bukkit.getPluginManager().registerEvents(new CrateGuiListener(), this);
        // 특수무기 획득권 우클릭은 EtCetera 특수무기 보관함(/특수무기)에서 처리한다.

        loadPlayerDatas();
    }

    public void loadPlayerDatas(){
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            PlayerCeilingRepository.getInstance().loadPlayerData(onlinePlayer);
            PlayerCeilingRepository.getInstance().grantPendingCeilingRewards(onlinePlayer);
            CrateUserRepository.getInstance().loadAsync(onlinePlayer);
        }
    }

    public void registerCommands(){
        getCommand("천장").setExecutor(new CeilingCommand());
        getCommand("천장확인").setExecutor(new CeilingCheckCommand());
        getCommand("천장복구").setExecutor(new CeilingRestoreCommand());
        getCommand("보상강제설정").setExecutor(new ForcedRewardCommand());
        CrateAdminCommand crateAdminCommand = new CrateAdminCommand();
        getCommand("보물상자").setExecutor(crateAdminCommand);
        getCommand("보물상자").setTabCompleter(crateAdminCommand);
    }

    @Override
    public void onDisable() {
        // 진행 중인 오픈 애니메이션은 보상을 지급하고 끝낸다.
        OpeningSession.finishAll();
        CrateHologramManager.getInstance().removeAll();
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            PlayerCeilingRepository.getInstance().savePlayerData(onlinePlayer);
        }
    }
}
