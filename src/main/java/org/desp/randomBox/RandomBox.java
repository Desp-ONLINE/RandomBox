package org.desp.randomBox;

import com.binggre.velocitysocketclient.VelocityClient;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.desp.randomBox.command.CeilingCommand;
import org.desp.randomBox.database.PlayerCeilingRepository;
import org.desp.randomBox.listener.BoxConfirmListener;
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

        registerCommands();

        Bukkit.getPluginManager().registerEvents(new BoxConfirmListener(), this);
        Bukkit.getPluginManager().registerEvents(new RandomBoxListener(), this);
        Bukkit.getPluginManager().registerEvents(new PlayerListener(), this);

        loadPlayerDatas();

    }

    public void loadPlayerDatas(){
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            PlayerCeilingRepository.getInstance().loadPlayerData(onlinePlayer);
        }
    }

    public void registerCommands(){
        getCommand("천장").setExecutor(new CeilingCommand());
    }

    @Override
    public void onDisable() {
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            PlayerCeilingRepository.getInstance().savePlayerData(onlinePlayer);
        }
    }
}
