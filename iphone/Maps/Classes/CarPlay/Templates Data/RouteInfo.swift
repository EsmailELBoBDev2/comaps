import CarPlay

/// Possible direction of a single navigation lane.
///
/// > Warning: Raw values MUST match the native ``routing::turns::lanes::LaneWay`` enum
/// > (see libs/routing/lanes/lane_way.hpp for details).
enum LaneWay: UInt8 {
  case none = 0
  case reverseLeft
  case sharpLeft
  case left
  case mergeToLeft
  case slightLeft
  case through
  case slightRight
  case mergeToRight
  case right
  case sharpRight
  case reverseRight

  /// Direction of the lane as a CarPlay angle.
  ///
  /// 0 degrees points straight ahead, negative values turn left and positive values turn right.
  /// Angles from https://developer.apple.com/download/files/CarPlay-Developer-Guide.pdf
  var angle: Measurement<UnitAngle> {
    let degrees: Double
    switch self {
    case .none, .through: degrees = 0
    case .slightLeft, .mergeToLeft: degrees = -45
    case .left: degrees = -90
    case .sharpLeft: degrees = -135
    case .reverseLeft: degrees = -180
    case .slightRight, .mergeToRight: degrees = 45
    case .right: degrees = 90
    case .sharpRight: degrees = 135
    case .reverseRight: degrees = 180
    }
    return Measurement(value: degrees, unit: .degrees)
  }

  /// SF Symbol used to draw this lane direction in the CarPlay lane strip.
  /// Approximate: sharp/normal turns share a glyph
  var symbolName: String {
    switch self {
    case .none, .through: return "arrow.up"
    case .slightLeft, .mergeToLeft: return "arrow.up.left"
    case .left, .sharpLeft: return "arrow.turn.up.left"
    case .reverseLeft: return "arrow.uturn.left"
    case .slightRight, .mergeToRight: return "arrow.up.right"
    case .right, .sharpRight: return "arrow.turn.up.right"
    case .reverseRight: return "arrow.uturn.right"
    }
  }
}

/// High-level driving maneuver for the upcoming turn.
///
/// > Warning: Raw values MUST match the native ``routing::turns::CarDirection`` enum
/// > (see libs/routing/turns.hpp for details).
enum CarDirection: UInt8 {
  case none = 0
  case goStraight
  case turnRight
  case turnSharpRight
  case turnSlightRight
  case turnLeft
  case turnSharpLeft
  case turnSlightLeft
  case uTurnLeft
  case uTurnRight
  case enterRoundAbout
  case leaveRoundAbout
  case stayOnRoundAbout
  case startAtEndOfStreet
  case reachedYourDestination
  case exitHighwayToLeft
  case exitHighwayToRight

  var isRoundabout: Bool {
    switch self {
    case .enterRoundAbout, .leaveRoundAbout, .stayOnRoundAbout: return true
    default: return false
    }
  }

  /// CarPlay maneuver type for the instrument cluster / HUD.
  @available(iOS 17.4, *)
  var cpManeuverType: CPManeuverType {
    switch self {
    case .none, .goStraight: return .straightAhead
    case .turnRight: return .rightTurn
    case .turnSharpRight: return .sharpRightTurn
    case .turnSlightRight: return .slightRightTurn
    case .turnLeft: return .leftTurn
    case .turnSharpLeft: return .sharpLeftTurn
    case .turnSlightLeft: return .slightLeftTurn
    case .uTurnLeft, .uTurnRight: return .uTurn
    case .enterRoundAbout, .stayOnRoundAbout: return .enterRoundabout
    case .leaveRoundAbout: return .exitRoundabout
    case .startAtEndOfStreet: return .startRoute
    case .reachedYourDestination: return .arriveAtDestination
    case .exitHighwayToLeft: return .highwayOffRampLeft
    case .exitHighwayToRight: return .highwayOffRampRight
    }
  }

  /// CarPlay junction type for the instrument cluster / HUD.
  @available(iOS 17.4, *)
  var cpJunctionType: CPJunctionType {
    return isRoundabout ? .roundabout : .intersection
  }
}

/// A single navigation lane: the directions it allows and, optionally, the recommended one.
@objc(MWMLaneInfo)
final class LaneInfo: NSObject {
  /// Directions this lane allows, as ``LaneWay`` raw values.
  let laneWays: [UInt8]
  /// Recommended direction (``LaneWay/none`` raw value when this lane is not recommended).
  let recommendedWay: UInt8

  @objc init(laneWays: [NSNumber], recommendedWay: UInt8) {
    self.laneWays = laneWays.map { $0.uint8Value }
    self.recommendedWay = recommendedWay
  }
}

@objc(MWMRouteInfo)
class RouteInfo: NSObject {
  let timeToTarget: TimeInterval
  let targetDistance: Double
  let targetUnits: UnitLength
  let distanceToTurn: Double
  let turnUnits: UnitLength
  let turnImageName: String?
  let nextTurnImageName: String?
  let speedMps: Double
  let speedLimitMps: Double?
  let roundExitNumber: Int
  let lanes: [LaneInfo]
  /// Structured (shield-resolved) components of the next turn's road, mirroring the native
  /// RouteSegment::RoadNameInfo. Used to build width-appropriate CarPlay instruction variants.
  /// Any may be empty.
  let roadName: String
  let roadRef: String
  let junctionRef: String
  let destinationRef: String
  let destination: String
  let isLink: Bool
  /// Upcoming maneuver direction, used for CarPlay instrument-cluster metadata.
  let carDirection: CarDirection
  let isLeftHandTraffic: Bool

  @objc init(timeToTarget: TimeInterval,
             targetDistance: Double,
             targetUnitsIndex: UInt8,
             distanceToTurn: Double,
             turnUnitsIndex: UInt8,
             turnImageName: String?,
             nextTurnImageName: String?,
             speedMps: Double,
             speedLimitMps: Double,
             roundExitNumber: Int,
             lanes: [LaneInfo],
             roadName: String,
             roadRef: String,
             junctionRef: String,
             destinationRef: String,
             destination: String,
             isLink: Bool,
             carDirectionIndex: UInt8,
             isLeftHandTraffic: Bool) {
    self.timeToTarget = timeToTarget
    self.targetDistance = targetDistance
    self.targetUnits = RouteInfo.unitLength(for: targetUnitsIndex)
    self.distanceToTurn = distanceToTurn
    self.turnUnits = RouteInfo.unitLength(for: turnUnitsIndex)
    self.turnImageName = turnImageName
    self.nextTurnImageName = nextTurnImageName
    self.speedMps = speedMps
    // speedLimitMps >= 0 means known limited speed.
    self.speedLimitMps = speedLimitMps < 0 ? nil : speedLimitMps
    self.roundExitNumber = roundExitNumber
    self.lanes = lanes
    self.roadName = roadName
    self.roadRef = roadRef
    self.junctionRef = junctionRef
    self.destinationRef = destinationRef
    self.destination = destination
    self.isLink = isLink
    self.carDirection = CarDirection(rawValue: carDirectionIndex) ?? .none
    self.isLeftHandTraffic = isLeftHandTraffic
  }


  /// > Warning: Order of enum values MUST BE the same with
  /// > native ``Distance::Units`` enum (see platform/distance.hpp for details).
  class func unitLength(for targetUnitsIndex: UInt8) -> UnitLength {
    switch targetUnitsIndex {
    case 0:
      return .meters
    case 1:
      return .kilometers
    case 2:
      return .feet
    case 3:
      return .miles
    default:
      return .meters
    }
  }
}
