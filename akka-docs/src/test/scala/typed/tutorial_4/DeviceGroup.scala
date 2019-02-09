/*
 * Copyright (C) 2009-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package typed.tutorial_4

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.PostStop
import akka.actor.typed.Signal
import akka.actor.typed.scaladsl.AbstractBehavior
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.Behaviors

//#device-group-full
//#device-group-register
object DeviceGroup {
  def apply(groupId: String): Behavior[DeviceGroupMessage] =
    Behaviors.setup(context => new DeviceGroup(context, groupId))

  trait DeviceGroupMessage

  private final case class DeviceTerminated(
    device:   ActorRef[Device.DeviceMessage],
    groupId:  String,
    deviceId: String) extends DeviceGroupMessage

}
//#device-group-register
//#device-group-register
//#device-group-remove

class DeviceGroup(context: ActorContext[DeviceGroup.DeviceGroupMessage], groupId: String)
  extends AbstractBehavior[DeviceGroup.DeviceGroupMessage] {
  import DeviceGroup._
  import DeviceManager._

  private var deviceIdToActor = Map.empty[String, ActorRef[Device.DeviceMessage]]

  context.log.info("DeviceGroup {} started", groupId)

  override def onMessage(msg: DeviceGroupMessage): Behavior[DeviceGroupMessage] =
    msg match {
      case trackMsg @ RequestTrackDevice(`groupId`, deviceId, replyTo) =>
        deviceIdToActor.get(deviceId) match {
          case Some(deviceActor) =>
            replyTo ! DeviceRegistered(deviceActor)
          case None =>
            context.log.info("Creating device actor for {}", trackMsg.deviceId)
            val deviceActor = context.spawn(Device(groupId, deviceId), s"device-$deviceId")
            //#device-group-register
            context.watchWith(deviceActor, DeviceTerminated(deviceActor, groupId, deviceId))
            //#device-group-register
            deviceIdToActor += deviceId -> deviceActor
            replyTo ! DeviceRegistered(deviceActor)
        }
        this

      case RequestTrackDevice(gId, _, _) =>
        context.log.warning(
          "Ignoring TrackDevice request for {}. This actor is responsible for {}.",
          gId, groupId
        )
        this
      //#device-group-register
      //#device-group-remove

      case RequestDeviceList(requestId, gId, replyTo) =>
        if (gId == groupId) {
          replyTo ! ReplyDeviceList(requestId, deviceIdToActor.keySet)
          this
        } else
          Behaviors.unhandled
      //#device-group-remove

      case DeviceTerminated(_, _, deviceId) =>
        context.log.info("Device actor for {} has been terminated", deviceId)
        deviceIdToActor -= deviceId
        this

      //#device-group-register
    }

  override def onSignal: PartialFunction[Signal, Behavior[DeviceGroupMessage]] = {
    case PostStop =>
      context.log.info("DeviceGroup {} stopped", groupId)
      this
  }
}
//#device-group-remove
//#device-group-register
//#device-group-full
