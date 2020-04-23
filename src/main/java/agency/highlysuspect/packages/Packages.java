package agency.highlysuspect.packages;

import agency.highlysuspect.packages.block.PBlocks;
import agency.highlysuspect.packages.block.entity.PBlockEntityTypes;
import agency.highlysuspect.packages.item.PItems;
import agency.highlysuspect.packages.junk.PBlockEventHandlers;
import agency.highlysuspect.packages.net.PNetCommon;
import net.fabricmc.api.ModInitializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Packages implements ModInitializer {
	public static final String MODID = "packages";
	public static final Logger LOGGER = LogManager.getLogger(MODID);
	
	@Override
	public void onInitialize() {
		PBlocks.onInitialize();
		PBlockEntityTypes.onInitialize();
		
		PItems.onInitialize();
		
		PBlockEventHandlers.onInitialize();
		PNetCommon.onInitialize();
	}
}
