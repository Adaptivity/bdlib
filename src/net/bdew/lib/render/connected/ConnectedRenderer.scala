/*
 * Copyright (c) bdew, 2013 - 2014
 * https://github.com/bdew/bdlib
 *
 * This mod is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * https://raw.github.com/bdew/bdlib/master/MMPL-1.0.txt
 */

package net.bdew.lib.render.connected

import cpw.mods.fml.client.registry.{ISimpleBlockRenderingHandler, RenderingRegistry}
import net.bdew.lib.block.BlockRef
import net.minecraft.block.Block
import net.minecraft.client.renderer.{RenderBlocks, Tessellator}
import net.minecraft.world.IBlockAccess
import net.minecraftforge.common.util.ForgeDirection
import org.lwjgl.opengl.GL11

object ConnectedRenderer extends ISimpleBlockRenderingHandler {
  val id = RenderingRegistry.getNextAvailableRenderId
  RenderingRegistry.registerBlockHandler(this)

  def doRenderItemSide(d: ForgeDirection, r: RenderBlocks, block: Block, meta: Int) = {
    val icon = r.getBlockIconFromSideAndMetadata(block, d.ordinal(), meta)
    Tessellator.instance.setNormal(d.offsetX, d.offsetY, d.offsetZ)
    d match {
      case ForgeDirection.DOWN => r.renderFaceYNeg(block, 0.0D, 0.0D, 0.0D, icon)
      case ForgeDirection.UP => r.renderFaceYPos(block, 0.0D, 0.0D, 0.0D, icon)
      case ForgeDirection.NORTH => r.renderFaceZNeg(block, 0.0D, 0.0D, 0.0D, icon)
      case ForgeDirection.SOUTH => r.renderFaceZPos(block, 0.0D, 0.0D, 0.0D, icon)
      case ForgeDirection.WEST => r.renderFaceXNeg(block, 0.0D, 0.0D, 0.0D, icon)
      case ForgeDirection.EAST => r.renderFaceXPos(block, 0.0D, 0.0D, 0.0D, icon)
      case _ => sys.error("Invalid side")
    }
  }

  override def renderInventoryBlock(block: Block, metadata: Int, modelID: Int, renderer: RenderBlocks) {
    val tessellator = Tessellator.instance
    GL11.glTranslatef(-0.5F, -0.5F, -0.5F)

    val edge = block.asInstanceOf[ConnectedTextureBlock].edgeIcon

    for (side <- ForgeDirection.VALID_DIRECTIONS) {
      tessellator.startDrawingQuads()
      doRenderItemSide(side, renderer, block, metadata)
      val m = ConnectedHelper.brightnessMultiplier(side)
      ConnectedHelper.draw(side, 8).doDraw(ConnectedHelper.Vec3F(0, 0, 0), edge)
      tessellator.draw()
    }

    GL11.glTranslatef(0.5F, 0.5F, 0.5F)
  }

  override def renderWorldBlock(world: IBlockAccess, x: Int, y: Int, z: Int, block: Block, modelId: Int, renderer: RenderBlocks): Boolean = {
    renderer.renderStandardBlock(block, x, y, z)

    val pos = ConnectedHelper.Vec3F(x, y, z)
    for (face <- ForgeDirection.VALID_DIRECTIONS)
      if (block.shouldSideBeRendered(world, x + face.offsetX, y + face.offsetY, z + face.offsetZ, face.ordinal()))
        drawFaceEdges(world, pos, face, block.asInstanceOf[ConnectedTextureBlock])

    return true
  }

  def drawFaceEdges(world: IBlockAccess, pos: ConnectedHelper.Vec3F, face: ForgeDirection, block: ConnectedTextureBlock) {
    val edge = block.edgeIcon
    val canConnect = block.canConnect(world, pos.asBlockRef, _: BlockRef)
    val sides = ConnectedHelper.faceAdjanced(face)

    val m = ConnectedHelper.brightnessMultiplier(face)
    Tessellator.instance.setColorOpaque_F(m, m, m)

    val fo = pos + face
    val b = block.getMixedBrightnessForBlock(world, fo.x.toInt, fo.y.toInt, fo.z.toInt)
    Tessellator.instance.setBrightness(b)

    val U = !canConnect((pos + sides.top).asBlockRef)
    val D = !canConnect((pos + sides.bottom).asBlockRef)
    val L = !canConnect((pos + sides.left).asBlockRef)
    val R = !canConnect((pos + sides.right).asBlockRef)

    if (!U && !R && !canConnect((pos + sides.top + sides.right).asBlockRef))
      ConnectedHelper.draw(face, 1).doDraw(pos, edge)

    if (!U && !L && !canConnect((pos + sides.top + sides.left).asBlockRef))
      ConnectedHelper.draw(face, 7).doDraw(pos, edge)

    if (!D && !R && !canConnect((pos + sides.bottom + sides.right).asBlockRef))
      ConnectedHelper.draw(face, 3).doDraw(pos, edge)

    if (!D && !L && !canConnect((pos + sides.bottom + sides.left).asBlockRef))
      ConnectedHelper.draw(face, 5).doDraw(pos, edge)

    if (U) ConnectedHelper.draw(face, 0).doDraw(pos, edge)
    if (D) ConnectedHelper.draw(face, 4).doDraw(pos, edge)
    if (R) ConnectedHelper.draw(face, 2).doDraw(pos, edge)
    if (L) ConnectedHelper.draw(face, 6).doDraw(pos, edge)

    if (block.isInstanceOf[BlockAdditionalRender]) {
      val overlays = block.asInstanceOf[BlockAdditionalRender].getFaceOverlays(world, pos.x.toInt, pos.y.toInt, pos.z.toInt, face)
      for (overlay <- overlays) {
        Tessellator.instance.setColorOpaque_F(overlay.color.r, overlay.color.g, overlay.color.b)
        ConnectedHelper.draw(face, 8).doDraw(pos, overlay.icon)
      }
    }
  }

  override def shouldRender3DInInventory(modelId: Int) = true
  override def getRenderId = id
}


