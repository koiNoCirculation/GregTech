package gregtech.common.metatileentities.electric.multiblockpart;

import codechicken.lib.raytracer.IndexedCuboid6;
import codechicken.lib.render.CCRenderState;
import codechicken.lib.vec.Matrix4;
import gregtech.api.gui.ModularUI;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.MetaTileEntityHolder;
import gregtech.api.metatileentity.WorkableTieredMetaTileEntity;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.render.OrientedOverlayRenderer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.ResourceLocation;

import java.util.List;

/**
 * Miner TE stub
 */
public class MetaTileEntityMiner extends WorkableTieredMetaTileEntity {
    public MetaTileEntityMiner(ResourceLocation metaTileEntityId, RecipeMap<?> recipeMap, OrientedOverlayRenderer renderer, int tier) {
        super(metaTileEntityId, recipeMap, renderer, tier);
    }

    @Override
    public MetaTileEntity createMetaTileEntity(MetaTileEntityHolder holder) {
        return null;
    }

    @Override
    protected ModularUI createUI(EntityPlayer entityPlayer) {
        return null;
    }

    @Override
    public String getTierlessTooltipKey() {
        return null;
    }

    @Override
    public void renderCovers(CCRenderState renderState, Matrix4 translation, BlockRenderLayer layer) {

    }

    @Override
    public void addCoverCollisionBoundingBox(List<? super IndexedCuboid6> collisionList) {

    }
}
