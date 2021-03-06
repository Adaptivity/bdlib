/*
 * Copyright (c) bdew, 2013 - 2014
 * https://github.com/bdew/bdlib
 *
 * This mod is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * https://raw.github.com/bdew/bdlib/master/MMPL-1.0.txt
 */

package net.bdew.lib.multiblock.gui

import net.bdew.lib.gui.widgets.{WidgetMultipane, WidgetSubcontainer}
import net.bdew.lib.gui.{Rect, _}
import net.bdew.lib.multiblock.data.OutputConfigPower
import net.bdew.lib.multiblock.interact.CIOutputFaces

class WidgetOutputs(p: Point, te: CIOutputFaces, rows: Int) extends WidgetSubcontainer(new Rect(p, 92, 19 * rows)) {
  for (i <- 0 until rows)
    add(new WidgetOutputRow(Point(0, 19 * i), te, i))
}

class WidgetOutputDisplay extends WidgetSubcontainer(Rect(20, 0, 72, 18))

class WidgetOutputRow(p: Point, te: CIOutputFaces, output: Int) extends WidgetMultipane(new Rect(p, 92, 18)) {
  add(new WidgetOutputIcon(Point(1, 1), te, output))
  val emptyPane = addPane(new WidgetSubcontainer(rect))
  val powerPane = addPane(new WidgetPowerOutput(te, output))

  def getActivePane =
    te.outputConfig.get(output) match {
      case Some(x: OutputConfigPower) => powerPane
      case _ => emptyPane
    }
}