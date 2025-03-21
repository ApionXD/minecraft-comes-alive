package net.mca.entity.interaction.gifts;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import net.mca.MCA;
import net.mca.resources.Resources;
import net.minecraft.resource.JsonDataLoader;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.JsonHelper;
import net.minecraft.util.profiler.Profiler;

import java.util.Map;

public class GiftLoader extends JsonDataLoader {
    protected static final Identifier ID = new Identifier(MCA.MOD_ID, "gifts");

    public GiftLoader() {
        super(Resources.GSON, "gifts");
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> data, ResourceManager manager, Profiler profiler) {
        GiftType.REGISTRY.clear();
        data.forEach((id, json) -> {
            try {
                GiftType.REGISTRY.add(GiftType.fromJson(id, JsonHelper.asObject(json, "root")));
            } catch (JsonParseException e) {
                MCA.LOGGER.error("Could not load gift type for id {}", id, e);
            }
        });

        //extend from mca entries to avoid copy pasta commonly used stuff
        for (GiftType type : GiftType.REGISTRY) {
            if (!type.getId().getNamespace().equals(MCA.MOD_ID) && type.getConditions().isEmpty()) {
                for (GiftType extendingType : GiftType.REGISTRY) {
                    if (extendingType.getId().getNamespace().equals(MCA.MOD_ID) && extendingType.getId().getPath().equals(type.getId().getPath())) {
                        type.extendFrom(extendingType);
                        break;
                    }
                }
            }
        }
    }
}
