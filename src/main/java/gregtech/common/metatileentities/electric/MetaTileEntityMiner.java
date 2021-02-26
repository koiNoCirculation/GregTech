package gregtech.common.metatileentities.electric;

import codechicken.lib.raytracer.IndexedCuboid6;
import codechicken.lib.render.CCRenderState;
import codechicken.lib.vec.Matrix4;
import gregtech.GregTechMod;
import gregtech.api.GTValues;
import gregtech.api.GregTechAPI;
import gregtech.api.capability.IEnergyContainer;
import gregtech.api.gui.GuiTextures;
import gregtech.api.gui.ModularUI;
import gregtech.api.gui.widgets.SlotWidget;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.MetaTileEntityHolder;
import gregtech.api.metatileentity.TieredMetaTileEntity;
import gregtech.api.metatileentity.WorkableTieredMetaTileEntity;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.render.OrientedOverlayRenderer;
import gregtech.api.util.GTLog;
import gregtech.common.blocks.BlockOre;
import gregtech.common.blocks.NonMetaBlocks;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StringUtils;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemStackHandler;

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

    private final int DURATION;

    private final int ENERGY_COST;

    //volatile
    private ChunkPos chunkPosController;

    /*
     * absolute XCoord
     * volatile
     * work.x << 4
     */
    private int absoluteStartX;

    /*
     * absolute ZCoord
     * volatile
     * work.z << 4
     */
    private int absoluteStartZ;

    //these are persistent states

    private MinerState state = MinerState.IDLE;

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

     /**
     * progress counter
     */
    private int progress = 0;

    //end of persistent stats


    public MetaTileEntityMiner(ResourceLocation metaTileEntityId, int tier) {
        super(metaTileEntityId, tier);
        ENERGY_COST =  BASE_ENERGY_COST << (2 * (tier - 1));
        DURATION = BASE_DURATION << (getTier() - 1);
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
            ItemStack tubes = importItems.getStackInSlot(0);
            if (!tubes.isEmpty() && tubes.getItem() == NonMetaBlocks.ITEM_MINING_TUBE) {
                tubes.shrink(1);
                world.setBlockState(new BlockPos(current.getX(), workingLayer, current.getZ()), NonMetaBlocks.MINING_TUBE.getDefaultState());
                success = true;
            }
        }
        return success;
    }

    protected boolean tryPutIntoInventory(ItemStack stack) {
        for (int i = 0; i < exportItems.getSlots(); i++) {
            ItemStack stackInSlot = exportItems.getStackInSlot(i);
            if(stackInSlot.isEmpty()) {

            } else if(stackInSlot.getItem() == stack.getItem()) {

            }
        }
        return false;
    }

    protected void dig() {
        /**
         * dig it here
         */
        GTLog.logger.info("Digging block<{}, {}>, absolute x,z = <{}, {}> at chunk<{},{}>", digX, digZ, absoluteStartX + digX, absoluteStartZ + digZ, work.x, work.z);
        Block block = getWorld().getBlockState(new BlockPos(absoluteStartX + digX, workingLayer, absoluteStartZ + digZ)).getBlock();
        if(block instanceof BlockOre) {
            ItemStack itemStack = new ItemStack(block, 1);
        }
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
                absoluteStartX = work.x >> 4;
                absoluteStartZ = work.z >> 4;
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
                    if(progress < DURATION) {
                        long cost = energyContainer.changeEnergy(ENERGY_COST);
                        if(cost < ENERGY_COST) {
                            //not enough energy
                            state = MinerState.IDLE;
                            progress = 0;
                        } else {
                            progress++;
                        }
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

    /**
     * serialization
     * @param data
     * @return
     */
    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound data) {
        NBTTagCompound nbtTagCompound = super.writeToNBT(data);
        //store machine state
        nbtTagCompound.setString("state", state.name());
        //store working chunk
        nbtTagCompound.setInteger("workChunkX", work.x);
        nbtTagCompound.setInteger("workChunkZ", work.z);
        //store working layer
        nbtTagCompound.setInteger("workingLayer", workingLayer);
        //store working coord
        nbtTagCompound.setInteger("digX", digX);
        nbtTagCompound.setInteger("digZ", digZ);
        //store progress
        nbtTagCompound.setInteger("progress", progress);
        return nbtTagCompound;
    }

    /**
     * deserialization
     * @param data
     */
    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        //restore state
        state = data.hasKey("state") ? MinerState.IDLE : MinerState.valueOf(data.getString("state"));
        //restore working chunk
        work = data.hasKey("workChunkX") && data.hasKey("workChunkZ") ? new ChunkPos(data.getInteger("workChunkX"), data.getInteger("workChunkZ")) :  new ChunkPos(chunkPosController.x - getChunkRadius(), chunkPosController.z - getChunkRadius());
        absoluteStartX = work.x << 4;
        absoluteStartZ = work.z << 4;
        //restore working layer
        workingLayer = data.hasKey("workingLayer") ?  data.getInteger("workingLayer") : getPos().getY() - 1;
        //restore working coord
        digX = data.getInteger("digX");
        digZ = data.getInteger("digZ");
        //restore progress
        progress = data.getInteger("progress");

    }



    @Override
    protected ModularUI createUI(EntityPlayer entityPlayer) {
        ModularUI.Builder builder = ModularUI.defaultBuilder().
                image(0, 0, 143, 75, GuiTextures.DISPLAY).
                label(5, 5, "采矿机", 0xFFFFFF).
                widget(new SlotWidget(importItems, 0, 20, 20, true, true)
                        .setBackgroundTexture(GuiTextures.SLOT, GuiTextures.IN_SLOT_OVERLAY));
        int outSlotStartX = 60;
        int outSlotStartY = 60;
        for(int row = 0; row < 3; row++) {
            for(int column = 0; column < 6; column++) {
                builder.widget(new SlotWidget(exportItems, row * 6 + column, outSlotStartX - 6 * 9 + row * 18, outSlotStartY + column * 18, true, false)
                        .setBackgroundTexture(GuiTextures.SLOT, GuiTextures.OUT_SLOT_OVERLAY));
            }
        }
        return builder.build(getHolder(), entityPlayer);
    }

    @Override
    protected IItemHandlerModifiable createImportItemHandler() {
        return new ItemStackHandler(1) {
            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return stack.getItem() == NonMetaBlocks.ITEM_MINING_TUBE;
            }
        };
    }

    /**
     * 18 output slots
     * row 3
     * column 6
     * @return
     */
    @Override
    protected IItemHandlerModifiable createExportItemHandler() {
        return new ItemStackHandler(18);
    }


    @Override
    public MetaTileEntity createMetaTileEntity(MetaTileEntityHolder holder) {
        MetaTileEntityMiner metaTileEntityMiner = new MetaTileEntityMiner(metaTileEntityId, getTier());
        metaTileEntityMiner.chunkPosController = new ChunkPos(holder.getPos().getX() >> 4, holder.getPos().getZ() >> 4);
        //initialize
        work = new ChunkPos(chunkPosController.x - getChunkRadius(), chunkPosController.z - getChunkRadius());
        absoluteStartX = work.x << 4;
        absoluteStartZ = work.z << 4;
        return metaTileEntityMiner;
    }
}
