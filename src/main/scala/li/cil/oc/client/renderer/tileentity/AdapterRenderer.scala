package li.cil.oc.client.renderer.tileentity

import li.cil.oc.client.Textures
import li.cil.oc.common.tileentity
import li.cil.oc.util.RenderState
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.Tessellator
import net.minecraft.client.renderer.texture.TextureMap
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer
import net.minecraft.client.renderer.vertex.DefaultVertexFormats
import net.minecraft.util.EnumFacing
import org.lwjgl.opengl.GL11

object AdapterRenderer extends TileEntitySpecialRenderer[tileentity.Adapter] {
  override def renderTileEntityAt(adapter: tileentity.Adapter, x: Double, y: Double, z: Double, f: Float, damage: Int) {
    RenderState.checkError(getClass.getName + ".renderTileEntityAt: entering (aka: wasntme)")

    if (adapter.openSides.contains(true)) {
      RenderState.pushAttrib()
      RenderState.disableEntityLighting()
      RenderState.makeItBlend()

      GlStateManager.pushMatrix()

      GlStateManager.translate(x + 0.5, y + 0.5, z + 0.5)
      GlStateManager.scale(1.0025, -1.0025, 1.0025)
      GlStateManager.translate(-0.5f, -0.5f, -0.5f)

      bindTexture(TextureMap.locationBlocksTexture)

      val t = Tessellator.getInstance
      val r = t.getWorldRenderer

      Textures.Block.bind()
      r.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX)

      val sideActivity = Textures.getSprite(Textures.Block.AdapterOn)

      if (adapter.isSideOpen(EnumFacing.DOWN)) {
        r.pos(0, 1, 0).tex(sideActivity.getMaxU, sideActivity.getMinV).endVertex()
        r.pos(1, 1, 0).tex(sideActivity.getMinU, sideActivity.getMinV).endVertex()
        r.pos(1, 1, 1).tex(sideActivity.getMinU, sideActivity.getMaxV).endVertex()
        r.pos(0, 1, 1).tex(sideActivity.getMaxU, sideActivity.getMaxV).endVertex()
      }

      if (adapter.isSideOpen(EnumFacing.UP)) {
        r.pos(0, 0, 0).tex(sideActivity.getMaxU, sideActivity.getMaxV).endVertex()
        r.pos(0, 0, 1).tex(sideActivity.getMaxU, sideActivity.getMinV).endVertex()
        r.pos(1, 0, 1).tex(sideActivity.getMinU, sideActivity.getMinV).endVertex()
        r.pos(1, 0, 0).tex(sideActivity.getMinU, sideActivity.getMaxV).endVertex()
      }

      if (adapter.isSideOpen(EnumFacing.NORTH)) {
        r.pos(1, 1, 0).tex(sideActivity.getMinU, sideActivity.getMaxV).endVertex()
        r.pos(0, 1, 0).tex(sideActivity.getMaxU, sideActivity.getMaxV).endVertex()
        r.pos(0, 0, 0).tex(sideActivity.getMaxU, sideActivity.getMinV).endVertex()
        r.pos(1, 0, 0).tex(sideActivity.getMinU, sideActivity.getMinV).endVertex()
      }

      if (adapter.isSideOpen(EnumFacing.SOUTH)) {
        r.pos(0, 1, 1).tex(sideActivity.getMinU, sideActivity.getMaxV).endVertex()
        r.pos(1, 1, 1).tex(sideActivity.getMaxU, sideActivity.getMaxV).endVertex()
        r.pos(1, 0, 1).tex(sideActivity.getMaxU, sideActivity.getMinV).endVertex()
        r.pos(0, 0, 1).tex(sideActivity.getMinU, sideActivity.getMinV).endVertex()
      }

      if (adapter.isSideOpen(EnumFacing.WEST)) {
        r.pos(0, 1, 0).tex(sideActivity.getMinU, sideActivity.getMaxV).endVertex()
        r.pos(0, 1, 1).tex(sideActivity.getMaxU, sideActivity.getMaxV).endVertex()
        r.pos(0, 0, 1).tex(sideActivity.getMaxU, sideActivity.getMinV).endVertex()
        r.pos(0, 0, 0).tex(sideActivity.getMinU, sideActivity.getMinV).endVertex()
      }

      if (adapter.isSideOpen(EnumFacing.EAST)) {
        r.pos(1, 1, 1).tex(sideActivity.getMinU, sideActivity.getMaxV).endVertex()
        r.pos(1, 1, 0).tex(sideActivity.getMaxU, sideActivity.getMaxV).endVertex()
        r.pos(1, 0, 0).tex(sideActivity.getMaxU, sideActivity.getMinV).endVertex()
        r.pos(1, 0, 1).tex(sideActivity.getMinU, sideActivity.getMinV).endVertex()
      }

      t.draw()

      RenderState.disableBlend()
      RenderState.enableEntityLighting()

      GlStateManager.popMatrix()
      RenderState.popAttrib()
    }

    RenderState.checkError(getClass.getName + ".renderTileEntityAt: leaving")
  }
}
