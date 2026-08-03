package com.silentclassroom;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.IntAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute;
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BoxShapeBuilder;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.ObjectMap;

/**
 * Loads and caches the original vp_game artwork (Ayan sprites, school
 * tilesets, kernel-panic robot art) as 3D textures.
 */
public class GameAssets implements Disposable {

    public static final String[] AYAN_DIRS = {
        "north", "north-east", "east", "south-east",
        "south", "south-west", "west", "north-west"
    };

    private final ObjectMap<String, Texture> textures = new ObjectMap<>();

    /** Get (and cache) a texture. Pixel-art filtering, repeat wrap. */
    public Texture get(String path) {
        Texture t = textures.get(path);
        if (t == null) {
            t = new Texture(Gdx.files.internal(path));
            t.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
            t.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);
            textures.put(path, t);
        }
        return t;
    }

    public Texture ayan(String dir) {
        return get("textures/ayan/" + dir + ".png");
    }

    /** Walking animation frame (0..AYAN_WALK_FRAMES-1) for a direction. */
    public static final int AYAN_WALK_FRAMES = 4;

    public Texture ayanWalk(String dir, int frame) {
        // 4-step stride cycle from 3 distinct poses: knee-bent, mid-stride, back-leg, mid-stride
        int[] cycle = {0, 1, 2, 1};
        return get("textures/ayan/walk/" + dir + "_" + cycle[frame % 4] + ".png");
    }

    /** Crouching pose (used while hiding under furniture) for a direction. */
    public Texture ayanCrouch(String dir) {
        return get("textures/ayan/crouch/" + dir + ".png");
    }

    public Texture robot() {
        return get("textures/robot.png");
    }

    public Texture tile(String name) {
        return get("textures/tiles/" + name + ".png");
    }

    /** Opaque material: texture modulated by white so highlights still work. */
    public Material texturedMaterial(Texture tex) {
        return new Material(
            TextureAttribute.createDiffuse(tex),
            ColorAttribute.createDiffuse(1f, 1f, 1f, 1f));
    }

    /** Transparent sprite material (billboard quads). */
    public Material spriteMaterial(Texture tex) {
        Material m = new Material(
            TextureAttribute.createDiffuse(tex),
            new BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA),
            IntAttribute.createCullFace(GL20.GL_NONE));
        return m;
    }

    /** Box with tiled UVs so small tiles repeat across large surfaces. */
    public Model texturedBox(ModelBuilder mb, float w, float h, float d,
                             Material mat, float repU, float repV) {
        mb.begin();
        MeshPartBuilder p = mb.part("box", GL20.GL_TRIANGLES,
            VertexAttributes.Usage.Position
                | VertexAttributes.Usage.Normal
                | VertexAttributes.Usage.TextureCoordinates,
            mat);
        p.setUVRange(0f, 0f, repU, repV);
        BoxShapeBuilder.build(p, w, h, d);
        return mb.end();
    }

    /**
     * Upright quad (anchored at its base, facing +Z) for character sprites.
     */
    public Model spriteQuad(ModelBuilder mb, float w, float h, Material mat) {
        return mb.createRect(
            -w / 2f, 0f, 0f,
             w / 2f, 0f, 0f,
             w / 2f, h,  0f,
            -w / 2f, h,  0f,
            0f, 0f, 1f,
            mat,
            VertexAttributes.Usage.Position
                | VertexAttributes.Usage.Normal
                | VertexAttributes.Usage.TextureCoordinates);
    }

    @Override
    public void dispose() {
        for (Texture t : textures.values()) t.dispose();
        textures.clear();
    }
}
