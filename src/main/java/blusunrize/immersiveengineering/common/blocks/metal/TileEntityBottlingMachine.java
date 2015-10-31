package blusunrize.immersiveengineering.common.blocks.metal;

import net.minecraft.block.Block;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidContainerItem;
import net.minecraftforge.fluids.IFluidHandler;
import net.minecraftforge.oredict.OreDictionary;
import blusunrize.immersiveengineering.api.crafting.BottlingMachineRecipe;
import blusunrize.immersiveengineering.common.Config;
import blusunrize.immersiveengineering.common.IEContent;
import blusunrize.immersiveengineering.common.blocks.multiblocks.MultiblockBottlingMachine;
import blusunrize.immersiveengineering.common.util.Utils;
import cofh.api.energy.EnergyStorage;
import cofh.api.energy.IEnergyReceiver;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class TileEntityBottlingMachine extends TileEntityMultiblockPart implements ISidedInventory, IEnergyReceiver, IFluidHandler
{
	public int facing = 2;
	public EnergyStorage energyStorage = new EnergyStorage(16000);
	public ItemStack[] inventory = new ItemStack[5];
	public int[] process = new int[5];
	public FluidTank tank = new FluidTank(32000);
	public ItemStack[] predictedOutput = new ItemStack[5];


	@Override
	public TileEntityBottlingMachine master()
	{
		if(offset[0]==0&&offset[1]==0&&offset[2]==0)
			return null;
		TileEntity te = worldObj.getTileEntity(xCoord-offset[0], yCoord-offset[1], zCoord-offset[2]);
		return te instanceof TileEntityBottlingMachine?(TileEntityBottlingMachine)te : null;
	}

	@Override
	public ItemStack getOriginalBlock()
	{
		if(pos<0)
			return null;
		ItemStack s = MultiblockBottlingMachine.instance.getStructureManual() [pos/6][1-pos%6/3][pos%3];
		return s!=null?s.copy():null;
	}
	@Override
	public void updateEntity()
	{
		if(!formed || pos!=4)
			return;

		if(worldObj.isRemote)
			return;
		boolean update = false;
		int consumed = Config.getInt("bottlingMachine_consumption");
		for(int i=0; i<inventory.length; i++)
			if(inventory[i]!=null)
			{
				if(process[i]>72)
				{
					if(this.energyStorage.extractEnergy(consumed, true)==consumed)
					{
						this.energyStorage.extractEnergy(consumed, false);
						process[i]++;
						if(process[i]<88 && predictedOutput[i] != null && predictedOutput[i].getItem() instanceof IFluidContainerItem && getFilledItem(predictedOutput[i], false)!=null)
							predictedOutput[i] = getFilledItem(predictedOutput[i], true);
					}
				}
				else
				{
					ItemStack filled = getFilledItem(inventory[i], false);
					if(this.energyStorage.extractEnergy(consumed, true)==consumed)
					{
						if(predictedOutput[i]==null || !OreDictionary.itemMatches(filled, predictedOutput[i],true))
							predictedOutput[i]=filled;
						this.energyStorage.extractEnergy(consumed, false);
						if(process[i]==0)
							update = true;
						process[i]++;
						if(filled!=null && process[i]>72)
							predictedOutput[i] = getFilledItem(inventory[i], true).copy();
					}
				}
				if(process[i]>120)
				{
					ItemStack output = predictedOutput[i]!=null? predictedOutput[i].copy():inventory[i].copy();
					TileEntity invOutput = worldObj.getTileEntity(xCoord+(facing==4?1:facing==5?-1:((mirrored?-1:1)*(facing==3?1:-1))), yCoord+1, zCoord+(facing==2?1:facing==3?-1:((mirrored?-1:1)*(facing==4?1:-1))));
					if((invOutput instanceof ISidedInventory && ((ISidedInventory)invOutput).getAccessibleSlotsFromSide(facing).length>0)
							||(invOutput instanceof IInventory && ((IInventory)invOutput).getSizeInventory()>0))
						output = Utils.insertStackIntoInventory((IInventory)invOutput, output, facing);

					if(output!=null)
					{
						ForgeDirection fd = ForgeDirection.getOrientation(facing);
						EntityItem ei = new EntityItem(worldObj, xCoord+.5+(facing==4?1:facing==5?-1:((mirrored?-1:1)*(facing==3?1:-1))), yCoord+1+.25, zCoord+.5+(facing==2?1:facing==3?-1:((mirrored?-1:1)*(facing==4?1:-1))), output.copy());
						ei.motionX = (0.075F * fd.offsetX);
						ei.motionY = 0.025000000372529D;
						ei.motionZ = (0.075F * fd.offsetZ);
						this.worldObj.spawnEntityInWorld(ei);
					}
					process[i]=-1;
					inventory[i]=null;
					predictedOutput[i]=null;
					update = true;
				}
			}

		if(update)
		{
			this.markDirty();
			worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
		}
	}

	public int getNextProcessID()
	{
		if(master()!=null)
			return master().getNextProcessID();
		for(int i=0; i<inventory.length; i++)
			if(inventory[i]==null)
			{
				int lowestProcess = 200;
				for(int j=0; j<inventory.length; j++)
				{
					if(inventory[j]!=null && process[j]<lowestProcess)
						lowestProcess = process[j];
				}
				if(lowestProcess==200 || lowestProcess>24)
					return i;
				else
					return -1;

			}
		return -1;
	}
	public ItemStack getFilledItem(ItemStack empty, boolean drainTank)
	{
		if(empty==null || tank.getFluid()==null)
			return null;
		BottlingMachineRecipe recipe = BottlingMachineRecipe.findRecipe(empty, tank.getFluid());
		if(recipe!=null && recipe.output!=null)
		{
			if(drainTank)
				tank.drain(recipe.fluidInput.amount, true);
			return recipe.output;
		}

		ItemStack filled = FluidContainerRegistry.fillFluidContainer(new FluidStack(tank.getFluid(),Integer.MAX_VALUE), empty);
		FluidStack fs = FluidContainerRegistry.getFluidForFilledItem(filled);
		if(filled!=null && fs.amount<=tank.getFluidAmount())
		{
			if(drainTank)
				tank.drain(fs.amount, true);
			return filled;
		}

		if(empty.getItem() instanceof IFluidContainerItem)
		{
			int accepted = ((IFluidContainerItem)empty.getItem()).fill(empty, tank.getFluid(), false);
			if(accepted > 0)
			{
				filled = empty.copy();	
				((IFluidContainerItem)filled.getItem()).fill(filled, new FluidStack(tank.getFluid(),accepted), true);
				if(drainTank)
					tank.drain(accepted, true);
				return filled;
			}
		}
		return null;
	}

	@Override
	public void readCustomNBT(NBTTagCompound nbt, boolean descPacket)
	{
		super.readCustomNBT(nbt, descPacket);
		facing = nbt.getInteger("facing");
		energyStorage.readFromNBT(nbt);

		tank.readFromNBT(nbt.getCompoundTag("tank"));

		NBTTagList invList = nbt.getTagList("inventory", 10);
		this.inventory = new ItemStack[5];
		for(int i=0; i<invList.tagCount(); i++)
		{
			NBTTagCompound itemTag = invList.getCompoundTagAt(i);
			int slot = itemTag.getByte("Slot") & 255;
			if(slot>=0 && slot<this.inventory.length)
				this.inventory[slot] = ItemStack.loadItemStackFromNBT(itemTag);
		}
		NBTTagList finishedList = nbt.getTagList("predictedOutput", 10);
		this.predictedOutput = new ItemStack[5];
		for(int i=0; i<finishedList.tagCount(); i++)
		{
			NBTTagCompound itemTag = finishedList.getCompoundTagAt(i);
			int slot = itemTag.getByte("Slot") & 255;
			if(slot>=0 && slot<this.predictedOutput.length)
				this.predictedOutput[slot] = ItemStack.loadItemStackFromNBT(itemTag);
		}
		process = nbt.getIntArray("process");
	}
	@Override
	public void writeCustomNBT(NBTTagCompound nbt, boolean descPacket)
	{
		super.writeCustomNBT(nbt, descPacket);
		nbt.setInteger("facing", facing);
		energyStorage.writeToNBT(nbt);

		NBTTagCompound tankTag = tank.writeToNBT(new NBTTagCompound());
		nbt.setTag("tank", tankTag);

		NBTTagList invList = new NBTTagList();
		for(int i=0; i<this.inventory.length; i++)
			if(this.inventory[i] != null)
			{
				NBTTagCompound itemTag = new NBTTagCompound();
				itemTag.setByte("Slot", (byte)i);
				this.inventory[i].writeToNBT(itemTag);
				invList.appendTag(itemTag);
			}
		nbt.setTag("inventory", invList);

		NBTTagList finishedList = new NBTTagList();
		for(int i=0; i<this.predictedOutput.length; i++)
			if(this.predictedOutput[i] != null)
			{
				NBTTagCompound itemTag = new NBTTagCompound();
				itemTag.setByte("Slot", (byte)i);
				this.predictedOutput[i].writeToNBT(itemTag);
				finishedList.appendTag(itemTag);
			}
		nbt.setTag("predictedOutput", finishedList);

		nbt.setIntArray("process", process);
	}

	@Override
	public boolean receiveClientEvent(int id, int arg)
	{
		return false;
	}

	@Override
	@SideOnly(Side.CLIENT)
	public AxisAlignedBB getRenderBoundingBox()
	{
		if(pos==4)
			return AxisAlignedBB.getBoundingBox(xCoord-1,yCoord,zCoord-1, xCoord+2,yCoord+2,zCoord+2);
		return AxisAlignedBB.getBoundingBox(xCoord,yCoord,zCoord, xCoord,yCoord,zCoord);
	}
	@Override
	@SideOnly(Side.CLIENT)
	public double getMaxRenderDistanceSquared()
	{
		return super.getMaxRenderDistanceSquared()*Config.getDouble("increasedTileRenderdistance");
	}
	@Override
	public float[] getBlockBounds()
	{
		if(pos<6)
			return new float[]{0,0,0,1,1,1};
		if(pos==10)
			return new float[]{facing<4?-.0625f:0,0,facing>3?-.0625f:0, facing<4?1.0625f:1,1,facing>3?1.0625f:1};

		float xMin = 0;
		float yMin = 0;
		float zMin = 0;
		float xMax = 1;
		float yMax = 1;
		float zMax = 1;
		int ff = this.mirrored?ForgeDirection.OPPOSITES[facing]:facing;

		if(pos%6<3)
		{
			xMin = facing==4?.5625f:0;
			zMin = facing==2?.5625f:0;
			xMax = facing==5?.4375f:1;
			zMax = facing==3?.4375f:1;
		}
		if((pos%3==0&&ff==4)||(pos%3==2&&ff==5))
			zMin = .4375f;
		else if((pos%3==0&&ff==5)||(pos%3==2&&ff==4))
			zMax = .5625f;
		else if((pos%3==0&&ff==3)||(pos%3==2&&ff==2))
			xMin = .4375f;
		else if((pos%3==0&&ff==2)||(pos%3==2&&ff==3))
			xMax = .5625f;

		return new float[]{xMin,yMin,zMin, xMax,yMax,zMax};
	}

	@Override
	public void invalidate()
	{
		super.invalidate();

		if(formed && !worldObj.isRemote)
		{
			//			int f = facing;
			TileEntity master = master();
			if(master==null)
				master = this;

			int startX = master.xCoord;
			int startY = master.yCoord;
			int startZ = master.zCoord;

			for(int yy=-1;yy<=1;yy++)
				for(int zz=-1;zz<=1;zz++)
					for(int xx=-1;xx<=1;xx++)
					{
						ItemStack s = null;
						int prevPos = 0;
						TileEntity te = worldObj.getTileEntity(startX+xx,startY+yy,startZ+zz);
						if(te instanceof TileEntityBottlingMachine)
						{
							s = ((TileEntityBottlingMachine)te).getOriginalBlock();
							prevPos = ((TileEntityBottlingMachine)te).pos;
							((TileEntityBottlingMachine)te).formed=false;
						}
						if(startX+xx==xCoord && startY+yy==yCoord && startZ+zz==zCoord)
							s = this.getOriginalBlock();
						if(s!=null && Block.getBlockFromItem(s.getItem())!=null)
						{
							if(startX+xx==xCoord && startY+yy==yCoord && startZ+zz==zCoord)
								worldObj.spawnEntityInWorld(new EntityItem(worldObj, xCoord+.5,yCoord+.5,zCoord+.5, s));
							else
							{
								if(Block.getBlockFromItem(s.getItem())==IEContent.blockMetalMultiblocks)
									worldObj.setBlockToAir(startX+xx,startY+yy,startZ+zz);
								worldObj.setBlock(startX+xx,startY+yy,startZ+zz, Block.getBlockFromItem(s.getItem()), s.getItemDamage(), 0x3);
							}
							TileEntity tile = worldObj.getTileEntity(startX+xx,startY+yy,startZ+zz);
							if(tile instanceof TileEntityConveyorBelt)
							{
								int l = prevPos%6/3;
								int w = prevPos%3;
								int fExpected = l==1?(w==0?ForgeDirection.OPPOSITES[facing]:facing): w<2?ForgeDirection.ROTATION_MATRIX[mirrored?0:1][facing]:facing;
								((TileEntityConveyorBelt)tile).facing = fExpected;
							}
						}
					}
		}
	}

	@Override
	public int getSizeInventory()
	{
		if(!formed)
			return 0;
		return inventory.length;
	}
	@Override
	public ItemStack getStackInSlot(int slot)
	{
		if(!formed)
			return null;
		if(master()!=null)
			return master().getStackInSlot(slot);
		if(slot<inventory.length)
			return inventory[slot];
		return null;
	}
	@Override
	public ItemStack decrStackSize(int slot, int amount)
	{
		if(!formed)
			return null;
		if(master()!=null)
			return master().decrStackSize(slot,amount);
		ItemStack stack = getStackInSlot(slot);
		if(stack != null)
			if(stack.stackSize <= amount)
				setInventorySlotContents(slot, null);
			else
			{
				stack = stack.splitStack(amount);
				if(stack.stackSize == 0)
					setInventorySlotContents(slot, null);
			}
		this.markDirty();
		return stack;
	}
	@Override
	public ItemStack getStackInSlotOnClosing(int slot)
	{
		if(!formed)
			return null;
		if(master()!=null)
			return master().getStackInSlotOnClosing(slot);
		ItemStack stack = getStackInSlot(slot);
		if(stack != null)
			setInventorySlotContents(slot, null);
		return stack;
	}
	@Override
	public void setInventorySlotContents(int slot, ItemStack stack)
	{
		if(!formed||worldObj.isRemote)
			return;
		if(master()!=null)
		{
			master().setInventorySlotContents(slot,stack);
			return;
		}
		inventory[slot] = stack;
		if(stack != null && stack.stackSize > getInventoryStackLimit())
			stack.stackSize = getInventoryStackLimit();
		this.markDirty();
		worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
	}
	@Override
	public String getInventoryName()
	{
		return "IEBottlingMachine";
	}
	@Override
	public boolean hasCustomInventoryName()
	{
		return false;
	}
	@Override
	public int getInventoryStackLimit()
	{
		return 1;
	}
	@Override
	public boolean isUseableByPlayer(EntityPlayer p_70300_1_)
	{
		return true;
	}
	@Override
	public void openInventory()
	{
	}
	@Override
	public void closeInventory()
	{
	}
	@Override
	public boolean isItemValidForSlot(int slot, ItemStack stack)
	{
		if(!formed||stack==null)
			return false;
		if(master()!=null)
			return master().isItemValidForSlot(slot,stack);
		return true;//this.getFilledItem(stack, false)!=null;
	}
	@Override
	public int[] getAccessibleSlotsFromSide(int side)
	{
		if(!formed)
			return new int[0];
		if(pos==9)
		{
			int next = getNextProcessID();
			if(next==-1)
				return new int[0];
			return new int[]{next};
		}
		return new int[0];
	}
	@Override
	public boolean canInsertItem(int slot, ItemStack stack, int side)
	{
		if(!formed)
			return true;
		if(master()!=null)
			return master().canInsertItem(slot,stack,side);
		return isItemValidForSlot(slot,stack);
	}
	@Override
	public boolean canExtractItem(int slot, ItemStack stack, int side)
	{
		return false;
	}

	@Override
	public boolean canConnectEnergy(ForgeDirection from)
	{
		return formed && pos==10 && from==ForgeDirection.UP;
	}
	@Override
	public int receiveEnergy(ForgeDirection from, int maxReceive, boolean simulate)
	{
		if(formed && pos==10 && from==ForgeDirection.UP && this.master()!=null)
		{
			TileEntityBottlingMachine master = master();
			int rec = master.energyStorage.receiveEnergy(maxReceive, simulate);
			master.markDirty();
			if(rec>0)
				worldObj.markBlockForUpdate(master().xCoord, master().yCoord, master().zCoord);
			return rec;
		}
		return 0;
	}
	@Override
	public int getEnergyStored(ForgeDirection from)
	{
		if(this.master()!=null)
			return this.master().energyStorage.getEnergyStored();
		return energyStorage.getEnergyStored();
	}
	@Override
	public int getMaxEnergyStored(ForgeDirection from)
	{
		if(this.master()!=null)
			return this.master().energyStorage.getMaxEnergyStored();
		return energyStorage.getMaxEnergyStored();
	}

	@Override
	public int fill(ForgeDirection from, FluidStack resource, boolean doFill)
	{
		if(resource==null)
			return 0;
		if(master()!=null)
			return master().fill(from, resource, doFill);
		int fill = tank.fill(resource, doFill);
		if(fill>0)
		{
			markDirty();
			worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
		}
		return fill<0?0:fill;
	}
	@Override
	public FluidStack drain(ForgeDirection from, FluidStack resource, boolean doDrain)
	{
		return null;
	}
	@Override
	public FluidStack drain(ForgeDirection from, int maxDrain, boolean doDrain)
	{
		return null;
	}
	@Override
	public boolean canFill(ForgeDirection from, Fluid fluid)
	{
		return formed && pos==4 && from==ForgeDirection.getOrientation(facing);
	}
	@Override
	public boolean canDrain(ForgeDirection from, Fluid fluid)
	{
		return false;
	}
	@Override
	public FluidTankInfo[] getTankInfo(ForgeDirection from)
	{
		if(this.master()!=null)
			return this.master().getTankInfo(from);
		return new FluidTankInfo[]{tank.getInfo()};
	}
}