package gregtech.common.metatileentities.electric;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import gregtech.api.GTValues;
import gregtech.api.capability.IEnergyContainer;
import gregtech.api.capability.IMultipleTankHandler;
import gregtech.api.capability.impl.RecipeLogicEnergy;
import gregtech.api.gui.GuiTextures;
import gregtech.api.gui.ModularUI;
import gregtech.api.gui.widgets.SlotWidget;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.MetaTileEntityHolder;
import gregtech.api.metatileentity.WorkableTieredMetaTileEntity;
import gregtech.api.recipes.CountableIngredient;
import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.RecipeMaps;
import gregtech.api.render.OrientedOverlayRenderer;
import gregtech.api.render.Textures;
import gregtech.api.util.GTLog;
import gregtech.api.util.GTUtility;
import gregtech.common.ConfigHolder;
import gregtech.common.blocks.BlockOre;
import gregtech.common.blocks.NonMetaBlocks;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemStackHandler;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Miner TE
 * 没有区载的矿机是没有灵魂的！
 */
public class MetaTileEntityMiner extends WorkableTieredMetaTileEntity {
    /*
     * base energy cost 8EU/t
     */
    private static final int BASE_ENERGY_COST = 8;

    /*
     * base work duration
     */
    private static final int BASE_DURATION = 20 * 4;

    /*
     * air can't be input of dummy recipe
     */
    public static ItemStack DUMMY;

    /**
     * dummy recipe on non-ore blocks
     */
    private static Recipe DUMMY_RECIPE;

    /**
     * MinerLogic/MTETrait is the true TileEntity!!!!!
     */
    class MinerLogic extends RecipeLogicEnergy {

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

        /*
         * working chunk
         */
        private ChunkPos work;

        /*
         * the current working layer
         */
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

        private boolean notEnoughTubes = false;

        //end of persistent stats

        /*
         * macerator recipe input slot for temp use
         */
        private final ItemStackHandler oreHandler = new ItemStackHandler(1);

        /*
         * if we are retrieving tubes
         */
        private boolean isRetrievingTubes = false;

        /*
         * if working chunk loaded
         * volatile, will not be stored
         */
        private boolean workingChunkLoaded = false;

        /*
         * if controller chunk loaded
         * volatile, will not be stored
         */
        private boolean controllerLoaded = false;

        /*
         * store recent 8 macerating recipes
         */
        private final LinkedHashMap<Recipe, Integer> lru = new LinkedHashMap<Recipe, Integer>(8) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Recipe, Integer> eldest) {
                return size() >= 8;
            }
        };

        public MinerLogic(MetaTileEntity tileEntity, RecipeMap<?> recipeMap, Supplier<IEnergyContainer> energyContainer) {
            super(tileEntity, recipeMap, energyContainer);
        }

        /**
         * unloadController调用时间:
         */
        @Override
        public void update() {
            if (!getMetaTileEntity().getWorld().isRemote) {
                /*
                 * Machine Processing: $workingEnabled
                 */
                if (workingEnabled) {
                    if(workingLayer >= getPos().getY()) {
                        //initial dig
                        digDeeper();
                    }
                    if(notEnoughTubes) {
                        notEnoughTubes = digDeeper();
                    }
                    else if (isRetrievingTubes) {
                        /*
                         * retrieve tube per second, cannot be interrupted until complete
                         */
                        if (workingLayer < MetaTileEntityMiner.this.getPos().getY()) {
                            if (progressTime >= 20) {
                                progressTime = 0;
                                retrieveTube();
                            }
                            progressTime++;
                        } else {
                            /*
                             * retreive complete
                             */
                            isRetrievingTubes = false;
                        }
                    } else {
                        if (progressTime > 0) {
                            updateRecipeProgress();
                        }
                        if (progressTime == 0) {
                            trySearchNewRecipe();
                        }
                    }
                }
                if (wasActiveAndNeedsUpdate) {
                    this.wasActiveAndNeedsUpdate = false;
                    setActive(false);
                }
            }
        }

        private Recipe searchLRU(IItemHandlerModifiable inputInventory, IMultipleTankHandler importFluids) {
            for (Recipe recipe : lru.keySet()) {
                if(recipe.matches(false, inputInventory, importFluids)) {
                    return recipe;
                }
            }
            return null;
        }
        @Override
        protected void trySearchNewRecipe() {
            /*
             * dig a block
             * if an ore block, then search recipe
             * else use dummy recipe(consume energy but does not give any)
             */
            ItemStack oreStack = dig().orElse(GTUtility.copy(DUMMY));
            oreHandler.setStackInSlot(0, oreStack);
            long maxVoltage = getMaxVoltage();
            Recipe currentRecipe = null;
            if(oreStack == DUMMY) {
                currentRecipe = DUMMY_RECIPE;
            } else {
                IMultipleTankHandler importFluids = getInputTank();
                Recipe prev = searchLRU(oreHandler, importFluids);
                if (prev != null) {
                    //if previous recipe still matches inputs, try to use it
                    currentRecipe = prev;
                } else {
                    boolean dirty = checkRecipeInputsDirty(oreHandler, importFluids);
                    if (dirty || forceRecipeRecheck) {
                        this.forceRecipeRecheck = false;
                        //else, try searching new recipe for given inputs
                        currentRecipe = findRecipe(maxVoltage, oreHandler, importFluids);
                        if (currentRecipe != null) {
                            lru.put(currentRecipe, 1);
                        } else {
                            currentRecipe = DUMMY_RECIPE;
                        }
                    }
                }
            }
            if (currentRecipe != null && setupAndConsumeRecipeInputs(currentRecipe)) {
                //forceLoadController调用时机: setupRecipe中如果之前的recipeEUT <= 0,即之前没有合成表/刚开始工作
                //同时调用forceLoadWorkingChunk
                if(recipeEUt <= 0) {
                    forceLoadController();
                    forceLoadWorkingChunk();
                }
                setupRecipe(currentRecipe);
            } else {
                //unloadController调用时机: 无合成表或者未能满足输入条件时调用
                //同时调用unloadwork
                unloadController();
                unloadWorkingChunk();
            }
        }

        @Override
        protected void setupRecipe(Recipe recipe) {
            int[] resultOverclock = calculateOverclock(BASE_ENERGY_COST, getMaxVoltage(), BASE_DURATION);
            setMaxProgress(resultOverclock[1]);
            this.recipeEUt = resultOverclock[0];
            this.progressTime = 1;
            this.fluidOutputs = GTUtility.copyFluidList(recipe.getFluidOutputs());
            int tier = getMachineTierForRecipe(recipe);
            this.itemOutputs = GTUtility.copyStackList(recipe.getResultItemOutputs(getOutputInventory().getSlots(), random, tier));
            if (this.wasActiveAndNeedsUpdate) {
                this.wasActiveAndNeedsUpdate = false;
            } else {
                this.setActive(true);
            }
        }

        /**
         * set block to air here
         * modify importInventory to oreHandler
         * @param recipe
         * @return
         */
        @Override
        protected boolean setupAndConsumeRecipeInputs(Recipe recipe) {
            if(getInputInventory().getStackInSlot(0).getItem() != Items.AIR) {
                GTLog.logger.info("Digging block<{}, {}>, absolute x,z = <{}, {}> at chunk<{},{}>", digX, digZ, absoluteStartX + digX, absoluteStartZ + digZ, work.x, work.z);
                BlockPos blockPos = new BlockPos(absoluteStartX + digX, workingLayer, absoluteStartZ + digZ);
                getWorld().setBlockToAir(blockPos);
            }
            int[] resultOverclock = calculateOverclock(recipe.getEUt(), getMaxVoltage(), recipe.getDuration());
            int totalEUt = resultOverclock[0] * resultOverclock[1];
            IItemHandlerModifiable exportInventory = getOutputInventory();
            IMultipleTankHandler importFluids = getInputTank();
            IMultipleTankHandler exportFluids = getOutputTank();
            return (totalEUt >= 0 ? getEnergyStored() >= (totalEUt > getEnergyCapacity() / 2 ? resultOverclock[0] : totalEUt) :
                    (getEnergyStored() - resultOverclock[0] <= getEnergyCapacity())) &&
                    MetaTileEntity.addItemsToItemHandler(exportInventory, true, recipe.getAllItemOutputs(exportInventory.getSlots())) &&
                    MetaTileEntity.addFluidsToFluidHandler(exportFluids, true, recipe.getFluidOutputs()) &&
                    recipe.matches(true, oreHandler, importFluids);
        }

        @Override
        protected void completeRecipe() {
            goNextPosition();
            GTLog.logger.info("going to next position <{}, {}>, absolute x,z = <{}, {}> at chunk<{},{}>", digX, digZ, absoluteStartX + digX, absoluteStartZ + digZ, work.x, work.z);
            super.completeRecipe();
        }

        /*
         * added load and unload chunk logic
         */
        @Override
        protected void updateRecipeProgress() {
            boolean drawEnergy = drawEnergy(recipeEUt);
            if (drawEnergy || (recipeEUt < 0)) {
                //as recipe starts with progress on 1 this has to be > only not => to compensate for it
                if (++progressTime > maxProgressTime) {
                    completeRecipe();
                }
            } else if (recipeEUt > 0) {
                //only set hasNotEnoughEnergy if this recipe is consuming recipe
                //generators always have enough energy
                //unloadController调用时机: 能源不足
                //同时调用unloadWorkingChunk
                unloadController();
                unloadWorkingChunk();
                this.hasNotEnoughEnergy = true;
                //if current progress value is greater than 2, decrement it by 2
                if (progressTime >= 2) {
                    if (ConfigHolder.insufficientEnergySupplyWipesRecipeProgress) {
                        this.progressTime = 1;
                    } else {
                        this.progressTime = Math.max(1, progressTime - 2);
                    }
                }
            }
        }

        protected int getChunkRadius() {
            return getTier();
        }

        protected void forceLoadController() {
            if(!controllerLoaded) {
                GTLog.logger.info("Force load controller chunk at {}, {}", chunkPosController.x, chunkPosController.z);
                ForgeChunkManager.forceChunk(ForgeChunkManager.requestTicket(GTValues.MODID, getWorld(), ForgeChunkManager.Type.NORMAL), chunkPosController);
                controllerLoaded = true;
            }
        }

        protected void forceLoadWorkingChunk() {
            if(!workingChunkLoaded) {
                GTLog.logger.info("Force load working chunk at {}, {}", work.x, work.z);
                ForgeChunkManager.forceChunk(ForgeChunkManager.requestTicket(GTValues.MODID, getWorld(), ForgeChunkManager.Type.NORMAL), work);
                workingChunkLoaded = true;
            }
        }

        protected void unloadWorkingChunk() {
            if(workingChunkLoaded) {
                GTLog.logger.info("unload working chunk at {}, {}", work.x, work.z);
                ForgeChunkManager.unforceChunk(ForgeChunkManager.requestTicket(GTValues.MODID, getWorld(), ForgeChunkManager.Type.NORMAL), chunkPosController);
                workingChunkLoaded = false;
            }
        }

        protected void unloadController() {
            if(controllerLoaded) {
                GTLog.logger.info("unload controller chunk at {}, {}", chunkPosController.x, chunkPosController.z);
                ForgeChunkManager.unforceChunk(ForgeChunkManager.requestTicket(GTValues.MODID, getWorld(), ForgeChunkManager.Type.NORMAL), work);
                controllerLoaded = false;
            }
        }

        /**
         * if yccord > 1 and next block is not bedrock
         * @return if we can dig deeper
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
            /*
             * if there's mining tube left
             */
            boolean success = false;
            BlockPos current = getPos();
            World world = getWorld();

            /*
             * do while there's no tube there
             */
           if(world.getBlockState(new BlockPos(current.getX(),workingLayer - 1,current.getZ())).getBlock() != NonMetaBlocks.MINING_TUBE) {
               ItemStack tubes = importItems.getStackInSlot(0);
               if (!tubes.isEmpty() && tubes.getItem() == NonMetaBlocks.ITEM_MINING_TUBE) {
                   tubes.shrink(1);
                   world.setBlockState(new BlockPos(current.getX(), workingLayer, current.getZ()), NonMetaBlocks.MINING_TUBE.getDefaultState());
                   workingLayer--;
                   success = true;
               }
           }
           return success;
        }

        protected void retrieveTube() {
            BlockPos blockPos = new BlockPos(getPos().getX(), workingLayer, getPos().getZ());
            if(getWorld().getBlockState(blockPos).getBlock() == NonMetaBlocks.MINING_TUBE) {
                if(importItems.getStackInSlot(0).isEmpty()) {
                    importItems.setStackInSlot(0, new ItemStack(NonMetaBlocks.ITEM_MINING_TUBE, 1));
                    getWorld().setBlockToAir(blockPos);
                } else if (importItems.getStackInSlot(0).getCount() < 64){
                    importItems.getStackInSlot(0).grow(1);
                    getWorld().setBlockToAir(blockPos);
                } else {
                    return;
                }
            }
            workingLayer++;
        }



        /**
         * called in MTETrait logic
         * @return ItemStack if successfully digged
         */
        protected Optional<ItemStack> dig() {
            /*
             * dig it here
             */
            Optional<ItemStack> result;
            BlockPos blockPos = new BlockPos(absoluteStartX + digX, workingLayer, absoluteStartZ + digZ);
            Block block = getWorld().getBlockState(blockPos).getBlock();
            if(block instanceof BlockOre) {
                result = Optional.of(new ItemStack(block, 1));
            } else {
                result = Optional.empty();
            }
            return result;
        }

        protected void goNextPosition() {
            if(digZ >= 16) {
                if(digX < 16) {
                    /*
                     * next line of current chunk
                     */
                    digX++;
                } else {
                    /*
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
                            if(canDigDeeper()) {
                               if(!digDeeper()) {
                                   notEnoughTubes = true;
                                   unloadWorkingChunk();
                                   unloadController();
                               }
                            } else {
                                /*
                                 * stop working on bedrock layers
                                 */
                                isRetrievingTubes = true;
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
        public NBTTagCompound serializeNBT() {
            NBTTagCompound nbtTagCompound = super.serializeNBT();
            //store machine state
            nbtTagCompound.setBoolean("isRetrievingTubes", isRetrievingTubes);
            //store working chunk
            nbtTagCompound.setInteger("workChunkX", work.x);
            nbtTagCompound.setInteger("workChunkZ", work.z);
            //store working layer
            nbtTagCompound.setInteger("workingLayer", workingLayer);
            //store working coord
            nbtTagCompound.setInteger("digX", digX);
            nbtTagCompound.setInteger("digZ", digZ);
            //store notEnoughTubes flag
            nbtTagCompound.setBoolean("notEnoughTubes", notEnoughTubes);
            return nbtTagCompound;
        }

        @Override
        public void deserializeNBT(NBTTagCompound data) {
            super.deserializeNBT(data);
            //restore working chunk
            work = data.hasKey("workChunkX") && data.hasKey("workChunkZ") ? new ChunkPos(data.getInteger("workChunkX"), data.getInteger("workChunkZ")) :  new ChunkPos(chunkPosController.x - getChunkRadius(), chunkPosController.z - getChunkRadius());
            absoluteStartX = work.x << 4;
            absoluteStartZ = work.z << 4;
            //restore working layer
            workingLayer = data.hasKey("workingLayer") ?  data.getInteger("workingLayer") : getPos().getY();
            //restore working coord
            digX = data.getInteger("digX");
            digZ = data.getInteger("digZ");
            //restore notEnoughTubes flag
            notEnoughTubes = data.getBoolean("notEnoughTubes");
        }
    }


    @Override
    protected RecipeLogicEnergy createWorkable(RecipeMap<?> recipeMap) {
        return new MinerLogic(this, recipeMap, () -> energyContainer);
    }





    @Override
    @SideOnly(Side.CLIENT)
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
     * @return 18 slots
     */
    @Override
    protected IItemHandlerModifiable createExportItemHandler() {
        return new ItemStackHandler(18);
    }


    @Override
    public MetaTileEntity createMetaTileEntity(MetaTileEntityHolder holder) {
        if(DUMMY == null) {
            DUMMY = new ItemStack(new ItemBlock(Blocks.BARRIER), 1);
            DUMMY_RECIPE = new Recipe(Lists.newArrayList(CountableIngredient.from(DUMMY)),
                    Lists.newArrayList(ItemStack.EMPTY),
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Collections.emptyList(),
                    ImmutableMap.of(), BASE_DURATION, BASE_ENERGY_COST, false);
        }
        //initialize here
        MetaTileEntityMiner metaTileEntityMiner = new MetaTileEntityMiner(metaTileEntityId, getTier(), RecipeMaps.MACERATOR_RECIPES, Textures.MINER_OVERLAY);
        MinerLogic workable = (MinerLogic) metaTileEntityMiner.workable;
        workable.chunkPosController = new ChunkPos(holder.getPos().getX() >> 4, holder.getPos().getZ() >> 4);
        workable.work = new ChunkPos(workable.chunkPosController.x - workable.getChunkRadius(), workable.chunkPosController.z - workable.getChunkRadius());
        workable.absoluteStartX = workable.work.x << 4;
        workable.absoluteStartZ = workable.work.z << 4;
        workable.workingLayer = holder.getPos().getY();
        return metaTileEntityMiner;
    }


    public MetaTileEntityMiner(ResourceLocation metaTileEntityId,  int tier, RecipeMap<?> recipeMap, OrientedOverlayRenderer renderer) {
        super(metaTileEntityId, recipeMap, renderer, tier);
    }
}
