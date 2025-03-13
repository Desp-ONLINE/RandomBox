package org.desp.randomBox;

import com.binggre.velocitysocketclient.VelocityClient;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.desp.randomBox.listener.BoxConfirmListener;
import org.desp.randomBox.listener.VelocityProxyListener;

public final class RandomBox extends JavaPlugin {

    @Getter
    private static RandomBox instance;
    @Override
    public void onEnable() {
        instance = this;

        VelocityClient.getInstance().getConnectClient().registerListener(VelocityProxyListener.class);

        Bukkit.getPluginManager().registerEvents(new BoxConfirmListener(), this);

    }

    @Override
    public void onDisable() {
    }
}
