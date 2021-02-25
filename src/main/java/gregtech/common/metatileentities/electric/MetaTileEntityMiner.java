package gregtech.common.metatileentities.electric;

import codechicken.lib.raytracer.IndexedCuboid6;
import codechicken.lib.render.CCRenderState;
import codechicken.lib.vec.Matrix4;
import gregtech.GregTechMod;
import gregtech.api.GTValues;
import gregtech.api.GregTechAPI;
import gregtech.api.capability.IEnergyContainer;
import gregtech.api.gui.ModularUI;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.MetaTileEntityHolder;
import gregtech.api.metatileentity.TieredMetaTileEntity;
import gregtech.api.metatileentity.WorkableTieredMetaTileEntity;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.render.OrientedOverlayRenderer;
import gregtech.api.util.GTLog;
import gregtech.common.blocks.NonMetaBlocks;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.ForgeChunkManager;

import java.util.List;

/**
 * Miner TE
 *
 */
public class MetaTileEntityMiner extends TieredMetaTileEntity {

    public enum MinerState {
        IDLE,
        RUNNING,
        RETRIEVING_TUBES
    }

    /**
     * base energy cost 8EU/t
     */
    private static final int BASE_ENERGY_COST = 8;

    /**
     * base work duration
     */
    private static final int BASE_DURATION = 20 * 4;

    private MinerState state = MinerState.IDLE;

    private ChunkPos chunkPosController;

    private ChunkPos work;

    private int workingLayer;

    /*
     * work XCoord in current chunk
     * 0 - 16
     */
    private int digX;

    /*
     * work ZCoord in current chunk
     * 0 - 16
     */
    private int digZ;

    /*
     * absolute XCoord
     * work.x << 4
     */
    private int absoluteStartX;

    /*
     * absolute ZCoord
     * work.z << 4
     */
    private int absoluteStartZ;

    /*
     * store mining tubes
     */
    private ItemStack tubes = new ItemStack(Items.AIR, 0);

    /**
     * progress counter
     */
    private int progress = 0;

    /*
     * store outputs
     */
    private ItemStack[] outputs = new ItemStack[9];

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

    protected int getEnergyCost() {
        return BASE_ENERGY_COST << (getTier());
    }

    protected int getDuration() {
        return BASE_DURATION;
    }

    /**
     * if yccord > 1 and next block is not bedrock
     * @return
     */
    protected boolean canDigDeeper() {
        BlockPos pos = getPos();
        return pos.getY() > 1 && getWorld().getBlockState(getPos().add(0,-1,0)).getBlock() != Blocks.BEDROCK;
    }

    /**
     * go to next layer
     * @return true if successfully digged
     */
    protected boolean digDeeper() {
        /**
         * if there's mining tube left
         */
        boolean success = false;
        BlockPos current = getPos();
        World world = getWorld();

        /**
         * do while there's no tube there
         */
        while (world.getBlockState(new BlockPos(current.getX(),workingLayer,current.getZ())) == NonMetaBlocks.MINING_TUBE) {
            workingLayer--;
        }
        if(canDigDeeper()) {
            if (!tubes.isEmpty() && tubes.getItem() == NonMetaBlocks.ITEM_MINING_TUBE) {
                tubes.shrink(1);
                world.setBlockState(new BlockPos(current.getX(), workingLayer, current.getZ()), NonMetaBlocks.MINING_TUBE.getDefaultState());
                success = true;
            }
        }
        return success;
    }

    protected void dig() {
        /**
         * dig it here
         */
        GTLog.logger.info("Digging block<{}, {}>, absolute x,z = <{}, {}> at chunk<{},{}>", digX, digZ, absoluteStartX + digX, absoluteStartZ + digZ, work.x, work.z);
        if(digZ >= 16) {
            if(digX < 16) {
                /**
                 * next line of current chunk
                 */
                digX++;
            } else {
                /**
                 * go to next chunk here
                 */


                unloadWorkingChunk();
                int newChunkX;
                int newChunkZ;
                if(work.z < chunkPosController.z + getChunkRadius()) {
                    newChunkX = work.x;
                    newChunkZ = work.z + 1;
                } else {
                    if(work.x < chunkPosController.x + getChunkRadius()) {
                        newChunkX = work.x + 1;
                        newChunkZ = chunkPosController.z - getChunkRadius();
                    } else {
                        newChunkX = chunkPosController.x - getChunkRadius();
                        newChunkZ = chunkPosController.z - getChunkRadius();
                        if(!digDeeper()) {
                            /*
                             * stop working on bedrock layers
                             */
                            state = MinerState.RETRIEVING_TUBES;
                        }
                    }
                }
                work = new ChunkPos(newChunkX, newChunkZ);
                absoluteStartX = work.x << 4;
                absoluteStartZ = work.z << 4;
                forceLoadWorkingChunk();
                digX = 0;
            }
            digZ = 0;
        } else {
            digZ++;
        }
    }

    @Override
    public void update() {
        super.update();
        if(!getWorld().isRemote) {
            switch (state) {
                case IDLE: break;
                case RUNNING: {
                    if(progress < getDuration()) {
                        progress++;
                    } else {
                        dig();
                        progress = 0;
                    }
                }break;
                case RETRIEVING_TUBES: {
                    /**
                     * get tubes back
                     */
                }
            }
        }
    }

    @Override
    protected ModularUI createUI(EntityPlayer entityPlayer) {
        return null;
    }

    @Override
    public MetaTileEntity createMetaTileEntity(MetaTileEntityHolder holder) {
        return null;
    }
}
