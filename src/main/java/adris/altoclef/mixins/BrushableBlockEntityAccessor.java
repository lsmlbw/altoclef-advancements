package adris.altoclef.mixins;

import net.minecraft.block.entity.BrushableBlockEntity;
import net.minecraft.loot.LootTable;
import net.minecraft.registry.RegistryKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(BrushableBlockEntity.class)
public interface BrushableBlockEntityAccessor {
    @Accessor("lootTable")
    RegistryKey<LootTable> altoclef$getLootTable();
}
