/*
 * Copyright (c) bdew, 2013 - 2014
 * https://github.com/bdew/bdlib
 *
 * This mod is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * https://raw.github.com/bdew/bdlib/master/MMPL-1.0.txt
 */

package net.bdew.lib.multiblock.tile

import net.minecraft.entity.player.EntityPlayer
import net.minecraft.util.{ChatComponentTranslation, ChatStyle, EnumChatFormatting}

abstract class TileControllerGui extends TileController {
  def openGui(player: EntityPlayer)

  def onClick(player: EntityPlayer) = {
    val missing = cfg.required.filter({ case (mod, cnt) => getNumOfMoudules(mod) < cnt })
    if (missing.size > 0) {
      player.addChatMessage(new ChatComponentTranslation("bdlib.multiblock.incomplete")
        .setChatStyle(new ChatStyle().setColor(EnumChatFormatting.RED)))
      for ((mod, cnt) <- missing)
        player.addChatMessage(
          new ChatComponentTranslation("- %s %s", Integer.valueOf(cnt),
            new ChatComponentTranslation(resources.getModuleName(mod)))
            .setChatStyle(new ChatStyle().setColor(EnumChatFormatting.RED)))
    } else openGui(player)
  }

}
