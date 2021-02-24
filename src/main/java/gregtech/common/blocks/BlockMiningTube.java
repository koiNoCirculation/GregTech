package gregtech.common.blocks;

import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.common.property.Properties;

/**
 * TODO: should only harvested by wrench
 */
public class BlockMiningTube extends Block {
    public static final String NAME = "mining_tube";

    @Override
    public BlockRenderLayer getBlockLayer() {
        return BlockRenderLayer.CUTOUT_MIPPED;
    }

    @Override
    public boolean doesSideBlockRendering(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing face) {
        return true;
    }

    @Override
    public boolean isOpaqueCube(IBlockState state) {
        return false;
    }

    @Override
    public boolean isFullCube(IBlockState state) {
        return false;
    }

    public BlockMiningTube() {
        super(Material.IRON);
        setHardness(24.0f);
        setResistance(12.0f);
        setUnlocalizedName(NAME);
        setSoundType(SoundType.METAL);
        setHarvestLevel("pickaxe",1);
        setCreativeTab(CreativeTabs.MISC);
        setRegistryName(NAME);

    }
}
