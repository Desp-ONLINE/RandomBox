package org.desp.randomBox.listener;

import com.binggre.velocitysocketclient.listener.VelocitySocketListener;
import com.binggre.velocitysocketclient.socket.SocketResponse;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;

public class VelocityProxyListener extends VelocitySocketListener {

    @Override
    public void onReceive(String[] strings) {
        String message = strings[0];
        Bukkit.broadcast(Component.text(message));
    }

    @Override
    public @NotNull SocketResponse onRequest(String... strings) {
        return null;
    }

    @Override
    public void onResponse(SocketResponse socketResponse) {

    }
}
