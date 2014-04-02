/*
 * Copyright (c) bdew, 2013 - 2014
 * https://github.com/bdew/bdlib
 *
 * This mod is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * https://raw.github.com/bdew/bdlib/master/MMPL-1.0.txt
 */

package net.bdew.lib.items

import net.minecraft.entity.item.EntityItem
import net.minecraft.item.{Item, ItemStack}
import net.minecraft.world.World
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.inventory.IInventory
import net.bdew.lib.Misc

object ItemUtils {
  def throwItemAt(world: World, x: Int, y: Int, z: Int, stack: ItemStack) {
    if ((stack == null) || world.isRemote) return
    val dx = world.rand.nextFloat * 0.8
    val dy = world.rand.nextFloat * 0.8
    val dz = world.rand.nextFloat * 0.8
    val entityitem: EntityItem = new EntityItem(world, x + dx, y + dy, z + dz, stack)
    entityitem.motionX = world.rand.nextGaussian * 0.05
    entityitem.motionY = world.rand.nextGaussian * 0.05 + 0.2
    entityitem.motionZ = world.rand.nextGaussian * 0.05
    world.spawnEntityInWorld(entityitem)
  }

  def dropItemToPlayer(world: World, player: EntityPlayer, stack: ItemStack) {
    if ((stack == null) || world.isRemote) return
    world.spawnEntityInWorld(new EntityItem(world, player.posX, player.posY, player.posZ, stack))
  }

  def isSameItem(stack1: ItemStack, stack2: ItemStack): Boolean = {
    if (stack1 == null || stack2 == null)
      return stack1 == stack2
    if (stack1.itemID != stack2.itemID)
      return false
    if (stack1.getHasSubtypes && stack2.getItemDamage != stack1.getItemDamage)
      return false
    return ItemStack.areItemStackTagsEqual(stack2, stack1)
  }

  def addStackToSlots(stack: ItemStack, inv: IInventory, slots: Iterable[Int], checkvalid: Boolean): ItemStack = {
    if (stack == null) return null

    // Try merging into existing slots
    for (slot <- slots if (!checkvalid || inv.isItemValidForSlot(slot, stack)) && isSameItem(stack, inv.getStackInSlot(slot))) {
      val target = inv.getStackInSlot(slot)
      val toAdd = Misc.min(target.getMaxStackSize - target.stackSize, inv.getInventoryStackLimit - target.stackSize, stack.stackSize)
      if (toAdd >= stack.stackSize) {
        target.stackSize += stack.stackSize
        stack.stackSize = 0
        inv.onInventoryChanged()
        return null
      } else if (toAdd > 0) {
        target.stackSize += toAdd
        stack.stackSize -= toAdd
        inv.onInventoryChanged()
      }
    }

    // Now find empty slots and stick any leftovers there
    for (slot <- slots if (!checkvalid || inv.isItemValidForSlot(slot, stack)) && inv.getStackInSlot(slot) == null) {
      if (inv.getInventoryStackLimit < stack.stackSize) {
        inv.setInventorySlotContents(slot, stack.splitStack(inv.getInventoryStackLimit))
      } else {
        inv.setInventorySlotContents(slot, stack.copy())
        stack.stackSize = 0
        return null
      }
    }

    return stack
  }

  def findItemInInventory(inv: IInventory, item: Item) =
    Range(0, inv.getSizeInventory).map(x => x -> inv.getStackInSlot(x))
      .find({ case (slot, stack) => stack != null && stack.getItem == item })
      .map({ case (slot, stack) => slot })
}