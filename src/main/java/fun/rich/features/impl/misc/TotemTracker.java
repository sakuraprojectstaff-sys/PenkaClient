package fun.rich.features.impl.misc;

import fun.rich.features.module.Module;
import fun.rich.features.module.ModuleCategory;
import fun.rich.utils.client.chat.ChatMessage;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class TotemTracker extends Module {

    @NonFinal static TotemTracker instance;

    Map<UUID, Integer> pops = new HashMap<>();

    @NonFinal long lastMsgMs = 0L;

    static final Style GRAY = Style.EMPTY.withColor(Formatting.GRAY).withItalic(false);
    static final Style GOLD = Style.EMPTY.withColor(Formatting.GOLD).withItalic(false);
    static final Style RED = Style.EMPTY.withColor(Formatting.RED).withItalic(false);
    static final Style DARK_GRAY = Style.EMPTY.withColor(Formatting.DARK_GRAY).withItalic(false);
    static final Style ARROW = Style.EMPTY.withColor(Formatting.GRAY).withItalic(false);

    public TotemTracker() {
        super("TotemTracker", "Totem pop notifications", ModuleCategory.MISC);
        instance = this;
    }

    public static TotemTracker getInstance() {
        return instance;
    }

    public void onTotemPop(PlayerEntity player, ItemStack totemStack) {
        if (!isState()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null || player == null) return;

        long now = System.currentTimeMillis();
        if (now - lastMsgMs < 50L) return;
        lastMsgMs = now;

        UUID id = player.getUuid();
        int count = pops.getOrDefault(id, 0) + 1;
        pops.put(id, count);

        boolean enchanted = totemStack != null && !totemStack.isEmpty() && totemStack.hasEnchantments();

        MutableText prefix = ChatMessage.brandmessage().copy().append(Text.literal(" -> ").setStyle(ARROW));

        MutableText msg = Text.empty().append(Text.literal("Снесли ").setStyle(GRAY));

        if (enchanted) {
            msg.append(Text.literal("зачарованный ").setStyle(GOLD));
        }

        msg.append(Text.literal("тотем у ").setStyle(GRAY))
                .append(Text.literal(player.getName().getString()).setStyle(RED))
                .append(Text.literal(" ").setStyle(GRAY))
                .append(Text.literal("(" + count + ")").setStyle(DARK_GRAY));

        mc.player.sendMessage(prefix.copy().append(msg), false);
    }

    @Override
    public void deactivate() {
        super.deactivate();
        pops.clear();
        lastMsgMs = 0L;
    }
}