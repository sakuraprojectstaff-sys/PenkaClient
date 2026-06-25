package fun.rich.mixins.client.screen.mainmenu;

import com.llamalad7.mixinextras.sugar.Local;
import fun.rich.features.impl.misc.SelfDestruct;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.option.ServerList;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

@Mixin(ServerList.class)
public class ServerListMixin {
    @Shadow
    @Final
    private List<ServerInfo> servers;

    @Inject(method = "loadFile", at = @At(value = "FIELD", target = "Lnet/minecraft/client/option/ServerList;hiddenServers:Ljava/util/List;", ordinal = 0))
    private void loadFileHook(CallbackInfo ci) {
        if (SelfDestruct.unhooked) return;
        if (servers == null) return;

        removeDuplicateSponsors();
        addMissingSponsors();
    }

    @Redirect(method = "saveFile", at = @At(value = "INVOKE", target = "Lnet/minecraft/nbt/NbtList;add(Ljava/lang/Object;)Z", ordinal = 0))
    private boolean saveFileHook(NbtList instance, Object o, @Local(ordinal = 0) ServerInfo info) {
        if (SelfDestruct.unhooked) {
            return instance.add((NbtElement) o);
        }

        if (info != null && isSponsorServer(info)) {
            return true;
        }

        return instance.add((NbtElement) o);
    }

    @Unique
    private static List<ServerInfo> rich$getSponsorServers() {
        return List.of(
                new ServerInfo("FunTime", "mc.funtime.su", ServerInfo.ServerType.LAN),
                new ServerInfo("HolyWorld", "mc.holyworld.ru", ServerInfo.ServerType.LAN),
                new ServerInfo("ReallyWorld", "mc.reallyworld.ru", ServerInfo.ServerType.LAN),
                new ServerInfo("SpookyTime", "mc.spookytime.net", ServerInfo.ServerType.LAN),
                new ServerInfo("AresMine", "mc.aresmine.ru", ServerInfo.ServerType.LAN)
        );
    }

    @Unique
    private static String rich$addr(ServerInfo info) {
        if (info == null || info.address == null) return "";
        return info.address.trim().toLowerCase();
    }

    @Unique
    private void removeDuplicateSponsors() {
        if (servers == null || servers.isEmpty()) return;

        List<ServerInfo> sponsorServers = rich$getSponsorServers();
        if (sponsorServers == null || sponsorServers.isEmpty()) return;

        Set<String> sponsorAddresses = new HashSet<>();
        for (ServerInfo sponsor : sponsorServers) {
            String addr = rich$addr(sponsor);
            if (!addr.isEmpty()) sponsorAddresses.add(addr);
        }

        Iterator<ServerInfo> iterator = servers.iterator();
        Set<String> seenAddresses = new HashSet<>();

        while (iterator.hasNext()) {
            ServerInfo server = iterator.next();
            String address = rich$addr(server);
            if (address.isEmpty()) continue;

            if (sponsorAddresses.contains(address)) {
                if (seenAddresses.contains(address)) {
                    iterator.remove();
                } else {
                    seenAddresses.add(address);
                }
            }
        }
    }

    @Unique
    private void addMissingSponsors() {
        if (servers == null) return;

        List<ServerInfo> sponsorServers = rich$getSponsorServers();
        if (sponsorServers == null || sponsorServers.isEmpty()) return;

        Set<String> existingAddresses = new HashSet<>();
        for (ServerInfo server : servers) {
            String addr = rich$addr(server);
            if (!addr.isEmpty()) existingAddresses.add(addr);
        }

        for (ServerInfo sponsor : sponsorServers) {
            String addr = rich$addr(sponsor);
            if (!addr.isEmpty() && !existingAddresses.contains(addr)) {
                servers.add(sponsor);
            }
        }
    }

    @Unique
    private boolean isSponsorServer(ServerInfo info) {
        if (info == null) return false;

        String addr = rich$addr(info);
        if (addr.isEmpty()) return false;

        List<ServerInfo> sponsorServers = rich$getSponsorServers();
        if (sponsorServers == null || sponsorServers.isEmpty()) return false;

        for (ServerInfo sponsor : sponsorServers) {
            if (addr.equals(rich$addr(sponsor))) {
                return true;
            }
        }
        return false;
    }
}