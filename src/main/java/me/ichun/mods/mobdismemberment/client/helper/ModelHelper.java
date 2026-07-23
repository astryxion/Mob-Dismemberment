package me.ichun.mods.mobdismemberment.client.helper;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingRenderer;
import net.minecraft.client.renderer.entity.model.EntityModel;
import net.minecraft.client.renderer.model.ModelRenderer;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.ResourceLocation;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Helper class to extract model parts from any entity's renderer (1.16.5 ModelRenderer).
 * <p>
 * Important: never look up Minecraft fields by mapped names like {@code "cubes"} —
 * those only exist in the dev environment. Production uses obfuscated names, so we
 * detect lists by element type / public APIs instead.
 */
public class ModelHelper {

    private static final Random CUBE_PROBE = new Random(0L);
    private static Field[] listFields;
    private static boolean listFieldsResolved;

    /**
     * Data class holding a model part and its position offset on the entity.
     */
    public static class PartData {
        public final ModelRenderer part;
        public final String name;
        public final float offsetX;
        public final float offsetY;
        public final float offsetZ;
        public final float centerX;
        public final float centerY;
        public final float centerZ;

        public PartData(ModelRenderer part, String name, float offsetX, float offsetY, float offsetZ) {
            this.part = part;
            this.name = name;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.offsetZ = offsetZ;

            float[] center = calculateGeometricCenter(part);
            this.centerX = center[0];
            this.centerY = center[1];
            this.centerZ = center[2];
        }
    }

    /**
     * Extract all model parts (bones) from an entity's model.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static List<PartData> extractModelParts(LivingEntity entity) {
        List<PartData> parts = new ArrayList<>();
        Set<ModelRenderer> processedParts = new HashSet<>();

        try {
            EntityRenderer<?> renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity);
            if (renderer instanceof LivingRenderer) {
                LivingRenderer livingRenderer = (LivingRenderer) renderer;
                EntityModel<?> model = livingRenderer.getModel();
                if (model != null) {
                    List<ModelRenderer> allParts = findAllModelParts(model);
                    for (ModelRenderer part : allParts) {
                        extractPartsRecursive(part, "", parts, 0, 0, 0, processedParts);
                    }
                }
            }
        } catch (Exception e) {
            // Failed to extract parts
        }

        return parts;
    }

    /**
     * Get the texture for an entity.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static ResourceLocation getEntityTexture(LivingEntity entity) {
        try {
            EntityRenderer renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity);
            if (renderer instanceof LivingRenderer) {
                LivingRenderer livingRenderer = (LivingRenderer) renderer;
                return livingRenderer.getTextureLocation(entity);
            }
        } catch (Exception e) {
            // Failed to get texture
        }
        return new ResourceLocation("textures/entity/zombie/zombie.png");
    }

    private static List<ModelRenderer> findAllModelParts(EntityModel<?> model) {
        List<ModelRenderer> modelParts = new ArrayList<>();
        Set<ModelRenderer> seen = new HashSet<>();

        Class<?> clazz = model.getClass();
        while (clazz != null && clazz != Object.class) {
            Field[] fields = clazz.getDeclaredFields();
            for (int i = 0; i < fields.length; i++) {
                Field field = fields[i];
                try {
                    field.setAccessible(true);
                    Object value = field.get(model);
                    if (value instanceof ModelRenderer && !seen.contains(value)) {
                        ModelRenderer part = (ModelRenderer) value;
                        modelParts.add(part);
                        seen.add(part);
                    }
                } catch (Exception ignored) {
                }
            }
            clazz = clazz.getSuperclass();
        }

        return modelParts;
    }

    private static void extractPartsRecursive(ModelRenderer part, String name, List<PartData> parts,
                                              float parentX, float parentY, float parentZ,
                                              Set<ModelRenderer> processedParts) {
        if (processedParts.contains(part)) {
            return;
        }
        processedParts.add(part);

        float worldX = parentX + part.x / 16.0F;
        float worldY = parentY + part.y / 16.0F;
        float worldZ = parentZ + part.z / 16.0F;

        if (hasCubes(part)) {
            parts.add(new PartData(part, name, worldX, worldY, worldZ));
        }

        try {
            List<ModelRenderer> children = getChildren(part);
            for (int i = 0; i < children.size(); i++) {
                ModelRenderer child = children.get(i);
                String childName = name.isEmpty() ? ("child" + i) : (name + ".child" + i);
                extractPartsRecursive(child, childName, parts, worldX, worldY, worldZ, processedParts);
            }
        } catch (Exception ignored) {
        }
    }

    private static Field[] getListFields() {
        if (!listFieldsResolved) {
            listFieldsResolved = true;
            List<Field> found = new ArrayList<>();
            Field[] fields = ModelRenderer.class.getDeclaredFields();
            for (int i = 0; i < fields.length; i++) {
                Field field = fields[i];
                if (List.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    found.add(field);
                }
            }
            listFields = found.toArray(new Field[0]);
        }
        return listFields;
    }

    /**
     * Uses the public {@link ModelRenderer#getRandomCube(Random)} API so this works
     * in production where private field names are obfuscated.
     */
    private static boolean hasCubes(ModelRenderer part) {
        try {
            part.getRandomCube(CUBE_PROBE);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private static List<ModelRenderer> getChildren(ModelRenderer part) {
        Field[] fields = getListFields();
        for (int i = 0; i < fields.length; i++) {
            try {
                Object value = fields[i].get(part);
                if (!(value instanceof List)) {
                    continue;
                }
                List<?> list = (List<?>) value;
                if (!list.isEmpty() && list.get(0) instanceof ModelRenderer) {
                    return (List<ModelRenderer>) list;
                }
            } catch (Exception ignored) {
            }
        }
        // Empty children lists need no recursion; identify by exclusion when cubes are present.
        for (int i = 0; i < fields.length; i++) {
            try {
                Object value = fields[i].get(part);
                if (!(value instanceof List)) {
                    continue;
                }
                List<?> list = (List<?>) value;
                if (list.isEmpty()) {
                    continue;
                }
                if (list.get(0) instanceof ModelRenderer.ModelBox) {
                    continue; // cubes list
                }
            } catch (Exception ignored) {
            }
        }
        return Collections.emptyList();
    }

    private static List<?> getCubes(ModelRenderer part) {
        Field[] fields = getListFields();
        for (int i = 0; i < fields.length; i++) {
            try {
                Object value = fields[i].get(part);
                if (!(value instanceof List)) {
                    continue;
                }
                List<?> list = (List<?>) value;
                if (!list.isEmpty() && list.get(0) instanceof ModelRenderer.ModelBox) {
                    return list;
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static float[] calculateGeometricCenter(ModelRenderer part) {
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        float maxZ = -Float.MAX_VALUE;
        boolean foundCubes = false;

        try {
            List<?> cubes = getCubes(part);
            if (cubes != null) {
                for (int i = 0; i < cubes.size(); i++) {
                    Object cube = cubes.get(i);
                    if (!(cube instanceof ModelRenderer.ModelBox)) {
                        continue;
                    }
                    ModelRenderer.ModelBox box = (ModelRenderer.ModelBox) cube;
                    minX = Math.min(minX, box.minX);
                    minY = Math.min(minY, box.minY);
                    minZ = Math.min(minZ, box.minZ);
                    maxX = Math.max(maxX, box.maxX);
                    maxY = Math.max(maxY, box.maxY);
                    maxZ = Math.max(maxZ, box.maxZ);
                    foundCubes = true;
                }
            }
        } catch (Exception ignored) {
        }

        if (foundCubes) {
            return new float[]{
                    (minX + maxX) / 2.0f,
                    (minY + maxY) / 2.0f,
                    (minZ + maxZ) / 2.0f
            };
        }

        return new float[]{0, 0, 0};
    }
}
