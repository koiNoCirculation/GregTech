package gregtech.common.blocks;

import gregtech.api.unification.material.type.Material;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemPickaxe;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.registries.IForgeRegistry;

/**
 * put blocks without meta here
 */
public class NonMetaBlocks {
    public static final BlockMiningTube MINING_TUBE = new BlockMiningTube();
    public static Item ITEM_MINING_TUBE;
    public static Item ITEM_PLACEHOLDER;

    public static void registerItems(IForgeRegistry<Item> registry) {
        ITEM_MINING_TUBE = new ItemBlock(NonMetaBlocks.MINING_TUBE).setRegistryName(MINING_TUBE.getRegistryName());
        registry.register(ITEM_MINING_TUBE);
    }

    public static void registerBlocks(IForgeRegistry<Block> registry) {
        registry.register(NonMetaBlocks.MINING_TUBE);

    }

    @SideOnly(Side.CLIENT)
    public static void registerModels() {
        ModelLoader.setCustomModelResourceLocation(ITEM_MINING_TUBE, 0, new ModelResourceLocation(MINING_TUBE.getRegistryName(), "normal"));
    }
}
