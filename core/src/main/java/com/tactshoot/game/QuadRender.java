package com.tactshoot.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;

/** A fullscreen triangle-strip quad in clip space, reused by sky and post FX. */
public class QuadRender {

    private final Mesh mesh;

    public QuadRender() {
        mesh = new Mesh(true, 4, 6,
                new VertexAttribute(VertexAttributes.Usage.Position, 2, "a_position"));
        mesh.setVertices(new float[]{
                -1f, -1f, 1f, -1f, 1f, 1f, -1f, 1f
        });
        mesh.setIndices(new short[]{0, 1, 2, 0, 2, 3});
    }

    public void draw(ShaderProgram shader) {
        Gdx.gl.glDisable(GL20.GL_CULL_FACE);
        mesh.bind(shader);
        mesh.render(shader, GL20.GL_TRIANGLES, 0, 6);
        mesh.unbind(shader);
    }

    public void dispose() {
        mesh.dispose();
    }
}
