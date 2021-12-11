package net.starlegacy.feature.gas.collectionfactors;

import net.starlegacy.feature.space.WorldFlags;
import org.bukkit.Location;

public class SpaceOnlyFactor extends CollectionFactor {
    @Override
    public boolean factor(Location location) {
        return WorldFlags.isFlagSet(location.getWorld(), WorldFlags.Flag.SPACE);
    }
}
