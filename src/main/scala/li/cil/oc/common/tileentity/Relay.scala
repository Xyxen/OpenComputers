package li.cil.oc.common.tileentity

import com.google.common.base.Charsets
import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import dan200.computercraft.api.peripheral.IComputerAccess
import li.cil.oc.Constants
import li.cil.oc.Localization
import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.api.Driver
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.api.network.Analyzable
import li.cil.oc.api.network.Connector
import li.cil.oc.api.network.Node
import li.cil.oc.api.network.Packet
import li.cil.oc.api.network.Visibility
import li.cil.oc.api.network.WirelessEndpoint
import li.cil.oc.common.InventorySlots
import li.cil.oc.common.Slot
import li.cil.oc.common.Tier
import li.cil.oc.common.item
import li.cil.oc.common.item.Delegator
import li.cil.oc.integration.Mods
import li.cil.oc.integration.opencomputers.DriverLinkedCard
import li.cil.oc.server.network.QuantumNetwork
import li.cil.oc.util.ExtendedNBT._
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraftforge.common.util.Constants.NBT
import net.minecraftforge.common.util.ForgeDirection

class Relay extends traits.SwitchLike with traits.ComponentInventory with traits.PowerAcceptor with Analyzable with WirelessEndpoint with QuantumNetwork.QuantumNode {
  lazy final val WirelessNetworkCardTier1 = api.Items.get(Constants.ItemName.WirelessNetworkCardTier1)
  lazy final val WirelessNetworkCardTier2 = api.Items.get(Constants.ItemName.WirelessNetworkCardTier2)
  lazy final val LinkedCard = api.Items.get(Constants.ItemName.LinkedCard)
  
  var wirelessTier = -1
  
  override def isWirelessEnabled = wirelessTier >= Tier.One
  
  var strength = Settings.get.maxWirelessRange(Tier.Two)

  var isRepeater = true

  var isLinkedEnabled = false

  var tunnel = "creative"

  val componentNodes = Array.fill(6)(api.Network.newNode(this, Visibility.Network).
    withComponent("relay").
    create())

  override def canUpdate = isServer

  // ----------------------------------------------------------------------- //

  @SideOnly(Side.CLIENT)
  override protected def hasConnector(side: ForgeDirection) = true

  override protected def connector(side: ForgeDirection) = sidedNode(side) match {
    case connector: Connector => Option(connector)
    case _ => None
  }

  override def energyThroughput = Settings.get.accessPointRate

  // ----------------------------------------------------------------------- //

  override def onAnalyze(player: EntityPlayer, side: Int, hitX: Float, hitY: Float, hitZ: Float): Array[Node] = {
    if (isWirelessEnabled) {
      player.addChatMessage(Localization.Analyzer.WirelessStrength(strength))
      Array(componentNodes(side))
    }
    else null
  }

  // ----------------------------------------------------------------------- //

  @Callback(direct = true, doc = """function():number -- Get the signal strength (range) used when relaying messages.""")
  def getStrength(context: Context, args: Arguments): Array[AnyRef] = synchronized(result(strength))

  @Callback(doc = """function(strength:number):number -- Set the signal strength (range) used when relaying messages.""")
  def setStrength(context: Context, args: Arguments): Array[AnyRef] = synchronized {
    strength = math.max(args.checkDouble(0), math.min(0, Settings.get.maxWirelessRange(wirelessTier)))
    result(strength)
  }

  @Callback(direct = true, doc = """function():boolean -- Get whether the access point currently acts as a repeater (resend received wireless packets wirelessly).""")
  def isRepeater(context: Context, args: Arguments): Array[AnyRef] = synchronized(result(isRepeater))

  @Callback(doc = """function(enabled:boolean):boolean -- Set whether the access point should act as a repeater.""")
  def setRepeater(context: Context, args: Arguments): Array[AnyRef] = synchronized {
    isRepeater = args.checkBoolean(0)
    result(isRepeater)
  }

  // ----------------------------------------------------------------------- //

  protected def queueMessage(source: String, destination: String, port: Int, answerPort: Int, args: Array[AnyRef]) {
    for (computer <- computers.map(_.asInstanceOf[IComputerAccess])) {
      val address = s"cc${computer.getID}_${computer.getAttachmentName}"
      if (source != address && Option(destination).forall(_ == address) && openPorts(computer).contains(port))
        computer.queueEvent("modem_message", Array(Seq(computer.getAttachmentName, Int.box(port), Int.box(answerPort)) ++ args.map {
          case x: Array[Byte] => new String(x, Charsets.UTF_8)
          case x => x
        }: _*))
    }
  }

  // ----------------------------------------------------------------------- //

  override def receivePacket(packet: Packet, source: WirelessEndpoint): Unit = {
    if (isWirelessEnabled) {
      tryEnqueuePacket(None, packet)
    }
  }

  override def receivePacket(packet: Packet): Unit = {
    if (isLinkedEnabled) {
      tryEnqueuePacket(None, packet)
    }
  }

  override def tryEnqueuePacket(sourceSide: Option[ForgeDirection], packet: Packet): Boolean = {
    if (Mods.ComputerCraft.isAvailable) {
      packet.data.headOption match {
        case Some(answerPort: java.lang.Double) => queueMessage(packet.source, packet.destination, packet.port, answerPort.toInt, packet.data.drop(1))
        case _ => queueMessage(packet.source, packet.destination, packet.port, -1, packet.data)
      }
    }
    super.tryEnqueuePacket(sourceSide, packet)
  }

  override protected def relayPacket(sourceSide: Option[ForgeDirection], packet: Packet): Unit = {
    super.relayPacket(sourceSide, packet)

    val tryChangeBuffer = sourceSide match {
      case Some(side) =>
        (amount: Double) => plugs(side.ordinal).node.asInstanceOf[Connector].tryChangeBuffer(amount)
      case _ =>
        (amount: Double) => plugs.exists(_.node.asInstanceOf[Connector].tryChangeBuffer(amount))
    }

    if (isWirelessEnabled && strength > 0 && (sourceSide.isDefined || isRepeater)) {
      val cost = Settings.get.wirelessCostPerRange(Tier.Two)
      if (tryChangeBuffer(-strength * cost)) {
        api.Network.sendWirelessPacket(this, strength, packet)
      }
    }

    if (isLinkedEnabled && sourceSide.isDefined) {
      val cost = packet.size / 32.0 + Settings.get.wirelessCostPerRange(wirelessTier) * Settings.get.maxWirelessRange(wirelessTier) * 5
      if (tryChangeBuffer(-cost)) {
        val endpoints = QuantumNetwork.getEndpoints(tunnel).filter(_ != this)
        for (endpoint <- endpoints) {
          endpoint.receivePacket(packet)
        }
      }
    }

    onSwitchActivity()
  }

  // ----------------------------------------------------------------------- //

  override protected def createNode(plug: Plug) = api.Network.newNode(plug, Visibility.Network).
    withConnector(math.round(Settings.get.bufferAccessPoint)).
    create()

  override protected def onPlugConnect(plug: Plug, node: Node) {
    super.onPlugConnect(plug, node)
    if (node == plug.node) {
      api.Network.joinWirelessNetwork(this)
    }
    if (plug.isPrimary)
      plug.node.connect(componentNodes(plug.side.ordinal()))
    else
      componentNodes(plug.side.ordinal).remove()
  }

  override protected def onPlugDisconnect(plug: Plug, node: Node) {
    super.onPlugDisconnect(plug, node)
    if (node == plug.node) {
      api.Network.leaveWirelessNetwork(this)
    }
    if (plug.isPrimary && node != plug.node)
      plug.node.connect(componentNodes(plug.side.ordinal()))
    else
      componentNodes(plug.side.ordinal).remove()
  }

  // ----------------------------------------------------------------------- //

  override protected def onItemAdded(slot: Int, stack: ItemStack) {
    super.onItemAdded(slot, stack)
    updateLimits(slot, stack)
  }
  
  private def updateLimits(slot: Int, stack: ItemStack) {
    Option(Driver.driverFor(stack, getClass)) match {
      case Some(driver) if driver.slot(stack) == Slot.CPU =>
        relayDelay = math.max(1, relayBaseDelay - ((driver.tier(stack) + 1) * relayDelayPerUpgrade).toInt)
      case Some(driver) if driver.slot(stack) == Slot.Memory =>
        relayAmount = math.max(1, relayBaseAmount + (Delegator.subItem(stack) match {
          case Some(ram: item.Memory) => (ram.tier + 1) * relayAmountPerUpgrade
          case _ => (driver.tier(stack) + 1) * (relayAmountPerUpgrade * 2)
        }))
      case Some(driver) if driver.slot(stack) == Slot.HDD =>
        maxQueueSize = math.max(1, queueBaseSize + (driver.tier(stack) + 1) * queueSizePerUpgrade)
      case Some(driver) if driver.slot(stack) == Slot.Card =>
        val descriptor = api.Items.get(stack)
        if (descriptor == WirelessNetworkCardTier1 || descriptor == WirelessNetworkCardTier2)
          wirelessTier = if (descriptor == WirelessNetworkCardTier1) Tier.One else Tier.Two
        if (descriptor == LinkedCard) {
          val data = DriverLinkedCard.dataTag(stack)
          if (data.hasKey(Settings.namespace + "tunnel")) {
            tunnel = data.getString(Settings.namespace + "tunnel")
            isLinkedEnabled = true
            QuantumNetwork.add(this)
          }
        }
      case _ => // Dafuq u doin.
    }
  }

  override protected def onItemRemoved(slot: Int, stack: ItemStack) {
    super.onItemRemoved(slot, stack)
    Driver.driverFor(stack, getClass) match {
      case driver if driver.slot(stack) == Slot.CPU => relayDelay = relayBaseDelay
      case driver if driver.slot(stack) == Slot.Memory => relayAmount = relayBaseAmount
      case driver if driver.slot(stack) == Slot.HDD => maxQueueSize = queueBaseSize
      case driver if driver.slot(stack) == Slot.Card =>
        wirelessTier = -1
        isLinkedEnabled = false
        QuantumNetwork.remove(this)
    }
  }

  override def getSizeInventory = InventorySlots.relay.length

  override def isItemValidForSlot(slot: Int, stack: ItemStack) =
    Option(Driver.driverFor(stack, getClass)).fold(false)(driver => {
      val provided = InventorySlots.relay(slot)
      val tierSatisfied = driver.slot(stack) == provided.slot && driver.tier(stack) <= provided.tier
      val cardTypeSatisfied = if (provided.slot == Slot.Card) api.Items.get(stack) == WirelessNetworkCardTier1 ||
        api.Items.get(stack) == WirelessNetworkCardTier2 || api.Items.get(stack) == LinkedCard else true
      tierSatisfied && cardTypeSatisfied
    })

  // ----------------------------------------------------------------------- //

  override def readFromNBTForServer(nbt: NBTTagCompound) {
    super.readFromNBTForServer(nbt)
    for (slot <- items.indices) items(slot) collect {
      case stack => updateLimits(slot, stack)
    }

    if (nbt.hasKey(Settings.namespace + "strength")) {
      strength = nbt.getDouble(Settings.namespace + "strength") max 0 min Settings.get.maxWirelessRange(wirelessTier)
    }
    if (nbt.hasKey(Settings.namespace + "isRepeater")) {
      isRepeater = nbt.getBoolean(Settings.namespace + "isRepeater")
    }
    nbt.getTagList(Settings.namespace + "componentNodes", NBT.TAG_COMPOUND).toArray[NBTTagCompound].
      zipWithIndex.foreach {
      case (tag, index) => componentNodes(index).load(tag)
    }
  }

  override def writeToNBTForServer(nbt: NBTTagCompound) = {
    super.writeToNBTForServer(nbt)
    nbt.setDouble(Settings.namespace + "strength", strength)
    nbt.setBoolean(Settings.namespace + "isRepeater", isRepeater)
    nbt.setNewTagList(Settings.namespace + "componentNodes", componentNodes.map {
      case node: Node =>
        val tag = new NBTTagCompound()
        node.save(tag)
        tag
      case _ => new NBTTagCompound()
    })
  }
}
