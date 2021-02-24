package gregtech.common.metatileentities.electric;

import codechicken.lib.raytracer.IndexedCuboid6;
import codechicken.lib.render.CCRenderState;
import codechicken.lib.vec.Matrix4;
import gregtech.GregTechMod;
import gregtech.api.GTValues;
import gregtech.api.GregTechAPI;
import gregtech.api.gui.ModularUI;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.MetaTileEntityHolder;
import gregtech.api.metatileentity.TieredMetaTileEntity;
import gregtech.api.metatileentity.WorkableTieredMetaTileEntity;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.render.OrientedOverlayRenderer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.ForgeChunkManager;

import java.util.List;

/**
 * Miner TE stub
 */
public class MetaTileEntityMiner extends TieredMetaTileEntity {

    private ChunkPos chunkPosController;

    private ChunkPos work;

    private ChunkPos begin;

    private ChunkPos end;

    private int workingLayer;

    /**
     * 0 - 16
     */
    private int digX;

    /**
     * 0 - 16
     */
    private int digZ;

    private int startX;

    private int startZ;

    public MetaTileEntityMiner(ResourceLocation metaTileEntityId, int tier) {
        super(metaTileEntityId, tier);
    }

    protected int getChunkRadius() {
        return getTier();
    }

    protected ChunkPos getChunkPosBegin() {
        //controller chunkposX - radius
        //controller chunkposZ - radius
        return new ChunkPos(chunkPosController.x - getChunkRadius(), chunkPosController.z - getChunkRadius());
    }

    protected ChunkPos getChunkPosEnd() {
        return new ChunkPos(chunkPosController.x + getChunkRadius(), chunkPosController.z + getChunkRadius());
    }

    protected void forceChunk(ChunkPos pos) {
        ForgeChunkManager.forceChunk(ForgeChunkManager.requestTicket(GTValues.MODID, getWorld(), ForgeChunkManager.Type.NORMAL), pos);
    }

    protected void unforceChunk(ChunkPos pos) {
        ForgeChunkManager.unforceChunk(ForgeChunkManager.requestTicket(GTValues.MODID, getWorld(), ForgeChunkManager.Type.NORMAL), pos);
    }

    protected void forceLoadController() {
        forceChunk(chunkPosController);
    }

    protected void forceLoadWorkingChunk() {
        forceChunk(work);
    }

    protected void unloadWorkingChunk() {
        unforceChunk(work);
    }

    protected void unloadController() {
        unforceChunk(chunkPosController);
    }

    /**
     * if yccord > 1 and next block is not bedrock
     * @return
     */
    protected boolean canDigDeeper() {
        BlockPos pos = getPos();
        return pos.getY() > 1 && getWorld().getBlockState(getPos().add(0,-1,0)).getBlock() != Blocks.BEDROCK;
    }

    protected void tryDig() {
        /**
         * dig it here
         */
        int z = digZ + 1;
        if(z >= 16) {
            digX = 0;
            /**
             * go to next chunk here
             */
        }
        digZ = 0;
    }


    @Override
    public MetaTileEntity createMetaTileEntity(MetaTileEntityHolder holder) {
        return null;
    }

    @Override
    protected ModularUI createUI(EntityPlayer entityPlayer) {
        return null;
    }
}
