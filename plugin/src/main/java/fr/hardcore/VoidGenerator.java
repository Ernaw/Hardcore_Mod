package fr.hardcore;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

import java.util.Random;

/**
 * Generateur de monde "vide" (void) utilise pour le lobby/hub.
 * Aucune terre, aucune structure, aucun mob naturel : juste le neant.
 * La plateforme du lobby est posee a la main par LobbyManager.
 */
public class VoidGenerator extends ChunkGenerator {

    @Override
    public void generateNoise(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {
        // rien : monde vide
    }

    @Override
    public boolean shouldGenerateNoise() {
        return false;
    }

    @Override
    public boolean shouldGenerateSurface() {
        return false;
    }

    @Override
    public boolean shouldGenerateCaves() {
        return false;
    }

    @Override
    public boolean shouldGenerateDecorations() {
        return false;
    }

    @Override
    public boolean shouldGenerateMobs() {
        return false;
    }

    @Override
    public boolean shouldGenerateStructures() {
        return false;
    }

    @Override
    public Location getFixedSpawnLocation(World world, Random random) {
        return new Location(world, 0.5, 101, 0.5);
    }
}
