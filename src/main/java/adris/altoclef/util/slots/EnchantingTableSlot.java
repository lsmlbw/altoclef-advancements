package adris.altoclef.util.slots;

public class EnchantingTableSlot extends Slot {
    public static final EnchantingTableSlot ITEM = new EnchantingTableSlot(0);
    public static final EnchantingTableSlot LAPIS = new EnchantingTableSlot(1);

    public EnchantingTableSlot(int windowSlot) {
        this(windowSlot, false);
    }

    private EnchantingTableSlot(int slot, boolean inventory) {
        super(slot, inventory);
    }

    @Override
    public int inventorySlotToWindowSlot(int inventorySlot) {
        return inventorySlot < 9 ? inventorySlot + 29 : inventorySlot + 2;
    }

    @Override
    protected int windowSlotToInventorySlot(int windowSlot) {
        return windowSlot >= 29 ? windowSlot - 29 : windowSlot - 2;
    }

    @Override
    protected String getName() {
        return "Enchanting Table";
    }
}
