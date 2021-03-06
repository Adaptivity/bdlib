/*
 * Copyright (c) bdew, 2013 - 2014
 * https://github.com/bdew/bdlib
 *
 * This mod is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * https://raw.github.com/bdew/bdlib/master/MMPL-1.0.txt
 */

package net.bdew.lib.recipes

import java.io.Reader

import cpw.mods.fml.common.ModAPIManager
import cpw.mods.fml.common.registry.GameRegistry
import net.bdew.lib.{BdLib, Misc}
import net.minecraft.block.Block
import net.minecraft.item.{Item, ItemStack}
import net.minecraftforge.oredict.{OreDictionary, ShapelessOreRecipe}

/**
 * Main recipe loader class
 * The input file is parsed by [[net.bdew.lib.recipes.RecipeParser]]
 * Into a list of [[net.bdew.lib.recipes.Statement]] subclasses
 * That are then executed here
 */

class RecipeLoader {

  /**
   * Create a new parser, override in subclasses to use an extended parser
   * @return New ConfigParser (or subclass) instance
   */
  def newParser() = new RecipeParser()

  /**
   * Current map of recipe characters to parser item references
   */
  var currCharMap = Map.empty[Char, StackRef].withDefault(x => error("Undefined recipe character '%s'", x))

  /**
   * Current map of class macros
   */
  var currClassMacros = Map.empty[String, String].withDefault(x => error("Undefined class macro '%s'", x))

  /**
   * Triggers an error with formatted string
   */
  def error(msg: String, params: Any*) = throw new StatementError(msg.format(params: _*))

  /**
   * List of unprocessed delayed statements
   */
  var delayedStatements = List.empty[DelayedStatement]

  /**
   * Looks up a recipe component
   * @param s Parser ItemStack reference
   * @return ItemStack or String that can be used as recipe components in MC functions
   */
  def getRecipeComponent(s: StackRef): AnyRef = s match {
    case StackOreDict(id) => id
    case StackMacro(ch) => getRecipeComponent(currCharMap(ch))
    case _ => getConcreteStack(s)
  }

  /**
   * Resolve class name if it's a macro
   */
  def getRealClassName(s: String): String = {
    if (s.startsWith("$")) {
      return currClassMacros(s.stripPrefix("$"))
    }
    return s
  }
  /**
   * Sanitize items from reflection
   * @param x The item
   * @param source Human readable source (used in errors and warnings)
   * @param meta Metadata or damage
   * @param cnt Stack size
   * @return
   */
  def sanitizeReflectedItem(x: AnyRef, source: String, meta: Int, cnt: Int): ItemStack = x match {
    case x: ItemStack =>
      val l = x.copy()
      l.stackSize = cnt
      if (meta != OreDictionary.WILDCARD_VALUE && meta != l.getItemDamage) {
        BdLib.logWarn("%s requested with meta %d but specifies %d", source, meta, l.getItemDamage)
        l.setItemDamage(meta)
      }
      return l
    case x: Item =>
      return new ItemStack(x, cnt, meta)
    case x: Block =>
      return new ItemStack(x, cnt, meta)
    case _ =>
      error("%s is %s which cannot be translated to an ItemStack", source, x.getClass.getName)
  }

  /**
   * Fetches an ItemStack using reflection from static fields
   * Item and Block instaces are converted to ItemStacks
   * @param clsName Name of class to fetch from (can be a class macro)
   * @param fldName Field name
   * @param meta Metadata or damage
   * @param cnt Stack size
   * @return Matching itemstack
   */
  def reflectStack(clsName: String, fldName: String, meta: Int, cnt: Int): ItemStack = {
    val realName = getRealClassName(clsName)
    val cls = Class.forName(realName)
    val fld = cls.getField(fldName).get(null)
    return sanitizeReflectedItem(fld, "%s.%s".format(realName, fldName), meta, cnt)
  }

  /**
   * Fetches an ItemStack using reflection from static getters
   * Item and Block instaces are converted to ItemStacks
   * @param clsName Name of class to fetch from (can be a class macro)
   * @param method Method name
   * @param param Parameter to method
   * @param meta Metadata or damage
   * @param cnt Stack size
   * @return Matching itemstack
   */
  def reflectStackGetter(clsName: String, method: String, param: String, meta: Int, cnt: Int): ItemStack = {
    var realName: String = clsName
    var realMethod: String = method

    //Some acrobatics to allow use with macros, even bare ones like $foo(bar)

    if ((realName.length == 0) && realMethod.startsWith("$")) {
      val v = getRealClassName(realMethod).split('.').reverse
      realName = v.tail.reverse.mkString(".")
      realMethod = v.head
    }
    if (realName.startsWith("$")) {
      realName = getRealClassName(clsName)
    }

    val cls = Class.forName(realName)
    val meth = cls.getMethod(realMethod, classOf[String])
    val res = meth.invoke(null, param)
    return sanitizeReflectedItem(res, "%s.%s(%s)".format(realName, realMethod, param), meta, cnt)
  }

  /**
   * Returns all possible ItemStacks that match a reference
   * Currently everything except OreDictionary references just returns one item
   * @param s Parser ItemStack reference
   * @param cnt Stack size
   * @return List of matching ItemStacks
   */
  def getAllConcreteStacks(s: StackRef, cnt: Int = 1): Iterable[ItemStack] = s match {
    case StackOreDict(id) =>
      import scala.collection.JavaConversions._
      val c = OreDictionary.getOres(id).map(_.copy())
      c.foreach(_.stackSize = cnt)
      return c
    case _ => Seq(getConcreteStack(s))
  }

  def notNull[T](v: T, err: => String) = if (v == null) error(err) else v

  /**
   * Returns an ItemStack that match a reference
   * This is the main StackRef resolution method
   * @param s Parser ItemStack reference
   * @param cnt Stack size
   * @return A matching ItemStack
   */
  def getConcreteStack(s: StackRef, cnt: Int = 1): ItemStack = s match {
    case StackOreDict(id) =>
      val l = OreDictionary.getOres(id)
      if (l.size == 0) error("Concrete ItemStack requested for OD entry '%s' that is empty", id)
      val s = l.get(0).copy()
      s.stackSize = cnt
      BdLib.logInfo("Concrete ItemStack for OD entry '%s' -> %s", id, s)
      return s
    case StackMacro(ch) => getConcreteStack(currCharMap(ch), cnt)
    case StackGeneric(mod, id) =>
      notNull(GameRegistry.findItemStack(mod, id, cnt), "Stack not found %s:%s".format(mod, id))
    case StackBlock(mod, id, meta) =>
      new ItemStack(notNull(GameRegistry.findBlock(mod, id), "Block not found %s:%s".format(mod, id)), cnt, meta)
    case StackItem(mod, id, meta) =>
      new ItemStack(notNull(GameRegistry.findItem(mod, id), "Item not found %s:%s".format(mod, id)), cnt, meta)
    case StackReflect(cls, field, meta) => reflectStack(cls, field, meta, cnt)
    case StackGetter(cls, method, param, meta) => reflectStackGetter(cls, method, param, meta, cnt)
  }

  /**
   * Looks up all characters used in the recipe
   * @param s The pattern
   * @return Map from character to recipe components and a boolean that means OD-aware methods should be used
   */
  def resolveRecipeComponents(s: Iterable[Char]): (Map[Char, AnyRef], Boolean) = {
    var comp = Map.empty[Char, AnyRef]
    var needOd = false
    for (x <- s if !comp.contains(x) && x != '_') {
      if (!currCharMap.contains(x)) error("Character %s is undefined", x)
      val r = getRecipeComponent(currCharMap(x))
      if (r.isInstanceOf[String]) needOd = true
      BdLib.logInfo("%s -> %s", x, r)
      comp += (x -> r)
    }
    return (comp, needOd)
  }

  /**
   * Process a single statement, override this to add more statements
   * @param s The statement
   */
  def processStatement(s: Statement): Unit = s match {
    case StIfHaveMod(mod, thn, els) =>
      if (Misc.haveModVersion(mod)) {
        BdLib.logInfo("ifMod: %s found", mod)
        processStatementsSafe(thn)
      } else {
        BdLib.logInfo("ifMod: %s not found", mod)
        processStatementsSafe(els)
      }

    case StIfHaveAPI(mod, thn, els) =>
      if (ModAPIManager.INSTANCE.hasAPI(mod)) {
        BdLib.logInfo("ifAPI: %s found", mod)
        processStatementsSafe(thn)
      } else {
        BdLib.logInfo("ifAPI: %s not found", mod)
        processStatementsSafe(els)
      }

    case StClearRecipes(res) =>
      BdLib.logInfo("Clearing recipes that produce %s", res)
      delayedStatements = delayedStatements.filter({
        x =>
          if (x.isInstanceOf[CraftingStatement])
            if (x.asInstanceOf[CraftingStatement].result == res) {
              BdLib.logInfo("Removing recipe %s", x)
              false
            } else true
          else true
      })

    case x: DelayedStatement =>
      delayedStatements :+= x

    case x =>
      BdLib.logError("Can't process %s - this is a programing bug!", x)
  }

  /**
   * Process a single delayed statement
   * @param st The statement
   */
  def processDelayedStatement(st: DelayedStatement) = st match {
    case StCharAssign(c, r) =>
      currCharMap += (c -> r)
      BdLib.logInfo("Added %s = %s", c, r)

    case StClassMacro(id, cls) =>
      currClassMacros += (id -> cls)
      BdLib.logInfo("Added def %s = %s", id, cls)

    case StRecipeShaped(rec, res, cnt) =>
      BdLib.logInfo("Adding shaped recipe %s => %s * %d", rec, res, cnt)
      val (comp, needOd) = resolveRecipeComponents(rec.mkString(""))
      val resStack = getConcreteStack(res, cnt)

      if (resStack.getItemDamage == OreDictionary.WILDCARD_VALUE) {
        BdLib.logInfo("Result meta is unset, defaulting to 0")
        resStack.setItemDamage(0)
      }

      if (needOd)
        Misc.addRecipeOD(resStack, rec, comp)
      else
        Misc.addRecipe(resStack, rec, comp)

      BdLib.logInfo("Done... result=%s, od=%s", resStack, needOd)

    case StRecipeShapeless(rec, res, cnt) =>
      BdLib.logInfo("Adding shapeless recipe %s => %s * %d", rec, res, cnt)
      val (comp, needOd) = resolveRecipeComponents(rec)
      val resStack = getConcreteStack(res, cnt)
      val recTrans = rec.toCharArray.map(comp(_))

      if (resStack.getItemDamage == OreDictionary.WILDCARD_VALUE) {
        BdLib.logInfo("Result meta is unset, defaulting to 0")
        resStack.setItemDamage(0)
      }

      if (needOd)
        GameRegistry.addRecipe(new ShapelessOreRecipe(resStack, recTrans: _*))
      else
        GameRegistry.addShapelessRecipe(resStack, recTrans: _*)

      BdLib.logInfo("Done... result=%s, od=%s", resStack, needOd)

    case StSmeltRecipe(in, out, cnt, xp) =>
      BdLib.logInfo("Adding smelting recipe %s => %s * %d (%f xp)", in, out, cnt, xp)
      val outStack = getConcreteStack(out, cnt)
      if (outStack.getItemDamage == OreDictionary.WILDCARD_VALUE) {
        BdLib.logInfo("Result meta is unset, defaulting to 0")
        outStack.setItemDamage(0)
      }
      for (inStack <- getAllConcreteStacks(in, 1)) {
        GameRegistry.addSmelting(inStack, outStack, xp)
        BdLib.logInfo("added %s -> %s", inStack, outStack)
      }

    case StIfHaveOD(od, thn, els) =>
      if (OreDictionary.getOres(od).size() > 0) {
        BdLib.logInfo("ifOreDict: %s found".format(od))
        processDelayedStatementsSafe(thn)
      } else {
        BdLib.logInfo("ifOreDict: %s not found".format(od))
        processDelayedStatementsSafe(els)
      }

    case x =>
      BdLib.logError("Can't process %s - this is a programing bug!", x)
  }

  /**
   * Process main delayed statements list, clear the list afterwards
   */
  def processDelayedStatements() {
    BdLib.logInfo("Processing %d delayed statements", delayedStatements.size)
    processDelayedStatementsSafe(delayedStatements)
    delayedStatements = List.empty
  }

  /**
   * Process a list of delayed statements and catch all exceptions
   * @param list The list to process
   */
  def processDelayedStatementsSafe(list: List[DelayedStatement]) {
    for (s <- list) {
      try {
        processDelayedStatement(s)
      } catch {
        case e: StatementError =>
          BdLib.logError("Error while processing %s: %s", s, e.getMessage)
        case e: Throwable =>
          BdLib.logErrorException("Error while processing %s", e, s)
      }
    }
  }

  /**
   * Process a list of statements and catch all exceptions
   * @param r The list to process
   */
  def processStatementsSafe(r: List[Statement]): Unit = {
    for (s <- r) {
      try {
        processStatement(s)
      } catch {
        case e: StatementError =>
          BdLib.logError("Error while processing %s: %s", s, e.getMessage)
        case e: Throwable =>
          BdLib.logErrorException("Error while processing %s", e, s)
      }
    }
  }

  def load(f: Reader) {
    BdLib.logInfo("Starting parsing")
    val r = newParser().doParse(f)
    BdLib.logInfo("Processing %d statements", r.size)
    processStatementsSafe(r)
    BdLib.logInfo("Done")
  }
}
