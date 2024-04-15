package kr.toxicity.animator.api.nms;

import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Map;

public interface NMS {
    void sendItemChange(@NotNull Player player, @NotNull Map<EquipmentSlot, ItemStack> itemStackMap, @NotNull Collection<Player> targetPlayers);
}
