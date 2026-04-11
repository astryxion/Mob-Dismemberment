package me.ichun.mods.mobdismemberment.client.helper;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.*;

/**
 * Helper class to extract model parts from any entity's renderer.
 */
public class ModelHelper {

    /**
     * True for {@link ModelPart} cube list elements in both dev (named types contain "Cube") and
     * production (intermediary names like {@code class_630$class_619} with no "Cube" substring).
     */
    private static boolean isModelPartCubeListElement(Object first) {
        if (first == null) {
            return false;
        }
        Class<?> c = first.getClass();
        if (ModelPart.class.equals(c.getDeclaringClass())) {
            return true;
        }
        return c.getName().contains("Cube");
    }

    /**
     * Data class holding a model part and its position offset on the entity.
     */
    public static class PartData {
        public final ModelPart part;
        public final String name;
        public final float offsetX;
        public final float offsetY;
        public final float offsetZ;
        // Geometric center offset for proper rotation
        public final float centerX;
        public final float centerY;
        public final float centerZ;

        public PartData(ModelPart part, String name, float offsetX, float offsetY, float offsetZ) {
            this.part = part;
            this.name = name;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.offsetZ = offsetZ;

            // Calculate geometric center
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
        Set<ModelPart> processedParts = new HashSet<>();

        try {
            EntityRenderer<?> renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity);
            if (renderer instanceof LivingEntityRenderer livingRenderer) {
                EntityModel<?> model = livingRenderer.getModel();
                if (model != null) {
                    // Find ALL ModelPart fields in the model class and superclasses
                    List<ModelPart> allParts = findAllModelParts(model);
                    for (ModelPart part : allParts) {
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
            if (renderer instanceof LivingEntityRenderer livingRenderer) {
                return livingRenderer.getTextureLocation(entity);
            }
        } catch (Exception e) {
            // Failed to get texture
        }
        return new ResourceLocation("minecraft", "textures/entity/zombie/zombie.png");
    }

    /**
     * Find ALL ModelPart fields in an EntityModel using reflection.
     */
    private static List<ModelPart> findAllModelParts(EntityModel<?> model) {
        List<ModelPart> modelParts = new ArrayList<>();
        Set<ModelPart> seen = new HashSet<>();

        // Check all fields in the model class and its superclasses
        Class<?> clazz = model.getClass();
        while (clazz != null && clazz != Object.class) {
            for (Field field : clazz.getDeclaredFields()) {
                try {
                    field.setAccessible(true);
                    Object value = field.get(model);
                    if (value instanceof ModelPart part && !seen.contains(part)) {
                        modelParts.add(part);
                        seen.add(part);
                    }
                } catch (Exception ignored) {}
            }
            clazz = clazz.getSuperclass();
        }

        return modelParts;
    }

    /**
     * Recursively extract all parts from a ModelPart hierarchy.
     * Only extracts parts that have cubes (actual geometry).
     * Uses processedParts to avoid duplicates when same part is referenced multiple times.
     */
    private static void extractPartsRecursive(ModelPart part, String name, List<PartData> parts,
                                               float parentX, float parentY, float parentZ,
                                               Set<ModelPart> processedParts) {
        // Skip if already processed
        if (processedParts.contains(part)) {
            return;
        }
        processedParts.add(part);

        // Calculate this part's world offset
        float worldX = parentX + part.x / 16.0F;
        float worldY = parentY + part.y / 16.0F;
        float worldZ = parentZ + part.z / 16.0F;

        // Only add parts that have actual geometry (cubes)
        if (hasCubes(part)) {
            // Detached clone: the renderer's EntityModel is shared and mutated every frame for other
            // entities of the same type. Holding live ModelPart references causes wrong poses/textures in production.
            parts.add(new PartData(cloneModelPartDetached(part), name, worldX, worldY, worldZ));
        }

        // Process children
        try {
            Map<String, ModelPart> children = getChildren(part);
            if (children != null) {
                for (Map.Entry<String, ModelPart> entry : children.entrySet()) {
                    String childName = name.isEmpty() ? entry.getKey() : name + "." + entry.getKey();
                    extractPartsRecursive(entry.getValue(), childName, parts, worldX, worldY, worldZ, processedParts);
                }
            }
        } catch (Exception ignored) {}
    }

    /**
     * Check if a ModelPart has any cubes (geometry).
     */
    /**
     * Deep-clone a {@link ModelPart} subtree with new parent/child nodes. Cuboid lists are shared by reference
     * (geometry is treated as immutable); pivots and rotations are copied so this tree is independent of the
     * living renderer model.
     */
    private static ModelPart cloneModelPartDetached(ModelPart original) {
        Map<String, ModelPart> origChildren = getChildren(original);
        Map<String, ModelPart> newChildren = new LinkedHashMap<>();
        if (origChildren != null) {
            for (Map.Entry<String, ModelPart> entry : origChildren.entrySet()) {
                newChildren.put(entry.getKey(), cloneModelPartDetached(entry.getValue()));
            }
        }
        List<?> cubeList = getCubeList(original);
        try {
            Constructor<ModelPart> ctor = ModelPart.class.getDeclaredConstructor(List.class, Map.class);
            ctor.setAccessible(true);
            ModelPart copy = ctor.newInstance(cubeList, newChildren);
            copyPartTransforms(original, copy);
            return copy;
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Mob Dismemberment: failed to clone ModelPart for detached gib", e);
        }
    }

    private static void copyPartTransforms(ModelPart from, ModelPart to) {
        to.x = from.x;
        to.y = from.y;
        to.z = from.z;
        to.xRot = from.xRot;
        to.yRot = from.yRot;
        to.zRot = from.zRot;
        to.visible = from.visible;
        to.skipDraw = from.skipDraw;
    }

    private static List<?> getCubeList(ModelPart part) {
        try {
            for (Field field : ModelPart.class.getDeclaredFields()) {
                if (field.getType() == List.class) {
                    field.setAccessible(true);
                    List<?> list = (List<?>) field.get(part);
                    if (list != null && !list.isEmpty()) {
                        Object first = list.get(0);
                        if (isModelPartCubeListElement(first)) {
                            return new ArrayList<>(list);
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return new ArrayList<>();
    }

    private static boolean hasCubes(ModelPart part) {
        try {
            // Access the cubes field via reflection
            for (Field field : ModelPart.class.getDeclaredFields()) {
                if (field.getType() == List.class) {
                    field.setAccessible(true);
                    List<?> list = (List<?>) field.get(part);
                    if (list != null && !list.isEmpty()) {
                        Object first = list.get(0);
                        if (isModelPartCubeListElement(first)) {
                            return true;
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    /**
     * Get children map from a ModelPart using reflection.
     */
    @SuppressWarnings("unchecked")
    private static Map<String, ModelPart> getChildren(ModelPart part) {
        try {
            for (Field field : ModelPart.class.getDeclaredFields()) {
                if (field.getType() == Map.class) {
                    field.setAccessible(true);
                    return (Map<String, ModelPart>) field.get(part);
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * Calculate the geometric center of a ModelPart's cubes.
     * Returns [centerX, centerY, centerZ] in model units (1/16 scale).
     */
    private static float[] calculateGeometricCenter(ModelPart part) {
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
        float maxX = Float.MIN_VALUE, maxY = Float.MIN_VALUE, maxZ = Float.MIN_VALUE;
        boolean foundCubes = false;

        try {
            // Find the cubes list field
            List<?> cubes = null;
            for (Field field : ModelPart.class.getDeclaredFields()) {
                if (field.getType() == List.class) {
                    field.setAccessible(true);
                    List<?> list = (List<?>) field.get(part);
                    if (list != null && !list.isEmpty()) {
                        Object first = list.get(0);
                        if (isModelPartCubeListElement(first)) {
                            cubes = list;
                            break;
                        }
                    }
                }
            }

            if (cubes != null) {
                // Get min/max fields from Cube class
                Field minXField = null, minYField = null, minZField = null;
                Field maxXField = null, maxYField = null, maxZField = null;

                Class<?> cubeClass = cubes.get(0).getClass();
                for (Field field : cubeClass.getDeclaredFields()) {
                    field.setAccessible(true);
                    String name = field.getName();
                    if (name.equals("minX") || name.contains("minX")) minXField = field;
                    else if (name.equals("minY") || name.contains("minY")) minYField = field;
                    else if (name.equals("minZ") || name.contains("minZ")) minZField = field;
                    else if (name.equals("maxX") || name.contains("maxX")) maxXField = field;
                    else if (name.equals("maxY") || name.contains("maxY")) maxYField = field;
                    else if (name.equals("maxZ") || name.contains("maxZ")) maxZField = field;
                }

                // If we couldn't find named fields, try by type (floats in order)
                if (minXField == null) {
                    List<Field> floatFields = new ArrayList<>();
                    for (Field field : cubeClass.getDeclaredFields()) {
                        if (field.getType() == float.class) {
                            field.setAccessible(true);
                            floatFields.add(field);
                        }
                    }
                    // Typically: minX, minY, minZ, maxX, maxY, maxZ
                    if (floatFields.size() >= 6) {
                        minXField = floatFields.get(0);
                        minYField = floatFields.get(1);
                        minZField = floatFields.get(2);
                        maxXField = floatFields.get(3);
                        maxYField = floatFields.get(4);
                        maxZField = floatFields.get(5);
                    }
                }

                if (minXField != null && maxXField != null) {
                    for (Object cube : cubes) {
                        float cubeMinX = minXField.getFloat(cube);
                        float cubeMinY = minYField.getFloat(cube);
                        float cubeMinZ = minZField.getFloat(cube);
                        float cubeMaxX = maxXField.getFloat(cube);
                        float cubeMaxY = maxYField.getFloat(cube);
                        float cubeMaxZ = maxZField.getFloat(cube);

                        minX = Math.min(minX, cubeMinX);
                        minY = Math.min(minY, cubeMinY);
                        minZ = Math.min(minZ, cubeMinZ);
                        maxX = Math.max(maxX, cubeMaxX);
                        maxY = Math.max(maxY, cubeMaxY);
                        maxZ = Math.max(maxZ, cubeMaxZ);
                        foundCubes = true;
                    }
                }
            }
        } catch (Exception ignored) {}

        if (foundCubes) {
            // Return center point (already in model units, will be divided by 16 when rendering)
            return new float[] {
                (minX + maxX) / 2.0f,
                (minY + maxY) / 2.0f,
                (minZ + maxZ) / 2.0f
            };
        }

        return new float[] {0, 0, 0};
    }
}
