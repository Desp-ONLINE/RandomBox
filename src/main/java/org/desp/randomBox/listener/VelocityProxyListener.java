package org.desp.randomBox.listener;

import com.binggre.velocitysocketclient.listener.VelocitySocketListener;
import com.binggre.velocitysocketclient.socket.SocketResponse;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.serializer.json.JSONComponentSerializer;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;

public class VelocityProxyListener extends VelocitySocketListener {

    @Override
    public void onReceive(String[] messages) {
        String serializedComponent = messages[0];

        Component message = JSONComponentSerializer.json().deserialize(serializedComponent);
        Bukkit.broadcast(message);
    }

    @Override
    public @NotNull SocketResponse onRequest(String... strings) {
        return null;
    }

    @Override
    public void onResponse(SocketResponse socketResponse) {

    }
}
