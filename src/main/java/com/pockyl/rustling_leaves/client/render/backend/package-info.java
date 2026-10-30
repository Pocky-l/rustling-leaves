/**
 * The per-version part of the leaf renderer: the only code that talks to Minecraft's GPU layer. {@link
 * com.pockyl.rustling_leaves.client.render.backend.RenderBackend} keeps the meshes on the GPU and draws the two passes
 * (with the render pipelines of {@code LeafPipelines} on 26.3),
 * {@link com.pockyl.rustling_leaves.client.render.backend.LevelRenderHook} hooks them into the level render and
 * {@link com.pockyl.rustling_leaves.client.render.backend.DataTexture} is the float texture published to shader packs.
 * A port to another Minecraft version rewrites this package and keeps its public members; the renderer that decides
 * what to draw and writes the quads ({@code client/LeafRenderer} through {@link
 * com.pockyl.rustling_leaves.client.render.QuadSink}) stays the same on every version.
 */
package com.pockyl.rustling_leaves.client.render.backend;
