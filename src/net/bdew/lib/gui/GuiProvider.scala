/*
 * Copyright (c) bdew, 2013 - 2014
 * https://github.com/bdew/bdlib
 *
 * This mod is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * https://raw.github.com/bdew/bdlib/master/MMPL-1.0.txt
 */

package net.bdew.lib.gui

import cpw.mods.fml.relauncher.{Side, SideOnly}
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.tileentity.TileEntity

trait GuiProvider {
  def guiId: Int

  type TEClass

  @SideOnly(Side.CLIENT)
  def getGui(te: TEClass, player: EntityPlayer): AnyRef
  def getContainer(te: TEClass, player: EntityPlayer): AnyRef

  @SideOnly(Side.CLIENT)
  def getGui(te: TileEntity, player: EntityPlayer): AnyRef = getGui(te.asInstanceOf[TEClass], player)
  def getContainer(te: TileEntity, player: EntityPlayer): AnyRef = getContainer(te.asInstanceOf[TEClass], player)
}
