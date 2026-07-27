package com.icraft;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Constantes compartidas entre common, fabric y neoforge.
 * Antes vivían dentro de ICraftMod (clase @Mod de NeoForge), lo cual
 * hacía que cualquier clase common que las necesitara dependiera de NeoForge.
 * ICraftMod (neoforge) ahora delega en ICraftConstants.MODID / .LOGGER.
 */
public final class ICraftConstants {
    public static final String MODID = "icraft";
    public static final Logger LOGGER = LogManager.getLogger(MODID);

    private ICraftConstants() {}
}
