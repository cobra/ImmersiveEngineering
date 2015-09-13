package blusunrize.immersiveengineering.common.util.compat;

import java.util.HashSet;
import java.util.Set;

import blusunrize.immersiveengineering.common.util.compat.hydcraft.HydCraftHelper;
import blusunrize.immersiveengineering.common.util.compat.mfr.MFRHelper;
import blusunrize.immersiveengineering.common.util.compat.minetweaker.MTHelper;
import blusunrize.immersiveengineering.common.util.compat.waila.WailaHelper;
import cpw.mods.fml.common.Loader;

public abstract class IECompatModule
{
	public static Set<IECompatModule> modules = new HashSet<IECompatModule>();
	static{
		if(Loader.isModLoaded("MineFactoryReloaded")) modules.add(new MFRHelper());
		if(Loader.isModLoaded("MineTweaker3")) modules.add(new MTHelper());
		if(Loader.isModLoaded("denseores")) modules.add(new DenseOresHelper());
		if(Loader.isModLoaded("EE3")) modules.add(new EE3Helper());
		if(Loader.isModLoaded("ForgeMicroblock")) modules.add(new FMPHelper());
		if(Loader.isModLoaded("Forestry")) modules.add(new ForestryHelper());
		if(Loader.isModLoaded("BackTools")) modules.add(new BacktoolsHelper());
		if(Loader.isModLoaded("Waila")) modules.add(new WailaHelper());
		if(Loader.isModLoaded("gregtech")) modules.add(new GregTechHelper());
		if(Loader.isModLoaded("HydCraft")) modules.add(new HydCraftHelper());
		if(Loader.isModLoaded("ThermalExpansion")) modules.add(new TEHelper());
	}

	public String modId;
	public IECompatModule(String modId)
	{
		this.modId = modId;
	}
	
	public abstract void init();
	public abstract void postInit();
}
